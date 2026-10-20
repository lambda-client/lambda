package com.lambda.pathing.session

import com.lambda.Lambda
import com.lambda.config.automation.AutomationConfig
import com.lambda.config.blocks.PathingRenderConfig
import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.managers.Manager
import com.lambda.interaction.managers.interacting.InteractManager
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.debug.ServerCorrections
import com.lambda.pathing.session.PathingSession.State
import com.lambda.pathing.world.store.TerrainStores
import com.lambda.pathing.world.streaming.PathingWorld
import com.lambda.util.player.MovementUtils.update
import com.lambda.util.world.ChunkPacketLoadContext

object PathingManager : Manager<PathingRequest>(0) {

	private var session: PathingSession? = null

	private var leaseHolder: PathingSession? = null

	@Volatile
	var renderConfig: PathingRenderConfig = AutomationConfig.DEFAULT.pathingRenderConfig
		private set

	val status: State get() = session?.state ?: State.Idle

	val telemetry: Telemetry get() = session?.telemetry() ?: Telemetry.EMPTY

	val isRouting: Boolean
		get() = session?.active == true || queuedRequest != null || waypointRoute.queuedWaypoints > 0

	private val waypointRoute = WaypointRoute()

	private var parked: PlanningJourney? = null

	private var parkedAt = 0L

	internal fun park(journey: PlanningJourney) {
		parked?.cancel()
		parked = journey
		parkedAt = System.currentTimeMillis()
	}

	private fun takeParked(): PlanningJourney? {
		val journey = parked ?: return null
		parked = null
		if (System.currentTimeMillis() - parkedAt <= PARKED_JOURNEY_MILLIS) return journey
		journey.cancel()
		return null
	}

	fun SafeContext.retarget(request: PathingRequest, goal: Stance): Boolean {
		val walk = session?.takeIf { it.request === request } ?: return false
		return with(walk) { retarget(goal) }
	}

	val world: PathingWorld? get() = session?.journey?.world

	fun isFinished(request: PathingRequest): Boolean = queuedRequest !== request && session?.isFinished(request) ?: true

	internal fun stateOf(request: PathingRequest): State? = when {
		queuedRequest === request -> State.Settling(request.goal.toString())
		else -> session?.takeIf { it.request === request }?.state
	}

	fun route(automated: Automated, waypoints: List<Stance>): PathingRequest? {
		val rehearsed = session?.takeIf { it.active && it.rehearsal?.waypoints == waypoints }
		if (rehearsed != null) {
			rehearsed.performPending = true
			return rehearsed.request
		}
		return waypointRoute.start(automated, waypoints)
	}

	fun rehearse(automated: Automated, waypoints: List<Stance>): PathingRequest? =
		waypointRoute.start(automated, waypoints, rehearse = true)

	fun cancel() {
		waypointRoute.drop()
		session?.cancel()
	}

	fun clear() {
		parked?.cancel()
		parked = null
		waypointRoute.drop()
		session?.release()
		session = null
		PlanningDebugChannel.reset()
	}

	override fun AutomatedSafeContext.handleRequest(request: PathingRequest) {
		if (!request.fresh) return

		if (request.goal != waypointRoute.expectedLeg) waypointRoute.drop()

		val goal = resolveGoalStance(player, request.goal)
		val previous = session
		var journey = previous?.takeJourney()
		if (previous?.active != true) {
			journey?.cancel()
			journey = takeParked()
		}

		waypointRoute.adoptWarmJourney(goal)?.let { warmed ->
			journey?.cancel()
			journey = warmed
		}

		previous?.takeIf { it.active }?.dumpEvent("superseded", "a new request toward $goal replaced the session")
		previous?.release()
		if (journey != null && journey.goal != goal && journey.retarget(goal) != true) {
			journey.cancel()
			journey = null
		}
		PlanningDebugChannel.reset()

		val walk = PathingSession(request, journey, waypointRoute)
		session = walk
		renderConfig = request.pathingRenderConfig

		with(walk) { start() }
	}

	init {
		RotationManager.holdVanillaOverridesWhile { session?.steersRotation == true }
		listenUnsafe<ConnectionEvent.Disconnect> {
			clear()
			TerrainStores.close()
		}
		listenUnsafe<WorldEvent.BlockUpdate.Client> { event ->
			if (event.world.isClient && event.oldState != event.newState &&
				!ChunkPacketLoadContext.isActive()
			) {
				session?.onBlockChanged(event.pos)
				parked?.world?.onBlockChanged(event.pos)
				waypointRoute.onBlockChanged(event.pos)
			}
		}
		listenUnsafe<PacketEvent.Receive.Pre> { event ->
			ServerCorrections.record(event.packet, Lambda.mc.player?.id)
		}
		listenUnsafe<WorldEvent.ChunkEvent.Load> { event ->
			val pos = event.chunk.pos
			session?.onChunkLoaded(pos.x, pos.z)
			parked?.world?.onChunkLoaded(pos.x, pos.z)
			waypointRoute.onChunkLoaded(pos.x, pos.z)
		}
		listenUnsafe<WorldEvent.ChunkEvent.Unload> { event ->
			val pos = event.chunk.pos
			val sections = event.chunk.sectionArray
			session?.onChunkUnloaded(pos.x, pos.z, sections)
			parked?.world?.onChunkUnloaded(pos.x, pos.z, sections)
			waypointRoute.onChunkUnloaded(pos.x, pos.z, sections)
		}
		listen<TickEvent.Pre> {
			val walk = session
			updateLeases(walk)
			if (walk != null) with(walk) { tick() }
		}

		listen<TickEvent.Input.Post> {
			val walk = session ?: return@listen
			with(walk) { useForThisTick() }
		}

		listen<MovementEvent.InputUpdate>({ Int.MAX_VALUE - 1 }) { event ->
			val walk = session ?: return@listen
			val input = with(walk) { inputForThisTick() } ?: return@listen
			event.input.update(
				forward = input.forward,
				strafe = input.strafe,
				jump = input.jump,
				sneak = input.sneak,
				sprint = input.sprint,
			)
		}
	}

	private fun updateLeases(walk: PathingSession?) {
		val steering = walk?.takeIf { it.steersRotation }
		leaseHolder?.takeIf { it !== steering }?.let { held ->
			RotationManager.release(held)
			InteractManager.release(held)
			leaseHolder = null
		}
		if (steering == null) return
		leaseHolder = steering
		RotationManager.lease(steering)
		if (steering.request.pathingConfig.openDoors) InteractManager.lease(steering) else InteractManager.release(steering)
	}

	private const val PARKED_JOURNEY_MILLIS = 30_000L
}
