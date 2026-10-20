package com.lambda.pathing.session

import com.lambda.Lambda
import com.lambda.context.SafeContext
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.interaction.managers.rotating.RotationMode
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.PlanDump
import com.lambda.pathing.execution.TrajectoryExecutionCursor
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.search.TrajectoryPlan
import com.lambda.threading.runSafeAutomated
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.CommunicationUtils.warn
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.chunk.ChunkSection

class PathingSession internal constructor(
	internal val request: PathingRequest,
	internal var journey: PlanningJourney?,
	private val waypointRoute: WaypointRoute,
) {
	sealed interface State {
		data object Idle : State

		data class Settling(val goal: String) : State
		data class Planning(val goal: String) : State
		data class Rehearsing(val goal: String, val improvements: Int, val bestFrames: Int?, val finished: Boolean) : State
		data class Aligning(val leg: Int, val yawError: Double) : State
		data class Executing(val frame: Int, val frames: Int, val leg: Int) : State

		data class Complete(val frames: Int, val legs: Int) : State
		data class Failed(val reason: String) : State
	}

	@Volatile
	var state: State = State.Idle
		internal set

	internal val telemetry = WalkTelemetry()

	internal val heartbeat = SessionHeartbeat()

	internal var active = true
		private set


	internal var leg = 0

	internal var goal: Stance = request.goal
		private set

	internal var passedWaypoints = 0

	internal fun remainingWaypoints(): List<Stance> = request.waypoints.drop(passedWaypoints)

	internal var replay: Replay? = null

	internal var rehearsal: Rehearsal? = request.rehearsal?.let(::Rehearsal)

	internal var performPending = false

	internal var tickInput: MovementSimulationInput? = null

	internal var afloatStart: AfloatStart? = null

	private var recoveryStreak = 0
	private var recoveredAt: Vec3d? = null

	internal val alignment = AlignmentController(this)
	internal val launcher = PlanningLauncher(this)
	internal val admission = TapeAdmission(this)
	internal val execution = ExecutionDriver(this)

	fun telemetry(): Telemetry = telemetry.snapshot().copy(
		leg = leg,
		queuedWaypoints = remainingWaypoints().size + waypointRoute.queuedWaypoints,
		sessionRestarts = launcher.restarts,
		rehearsal = rehearsal?.view(),
	)

	fun isFinished(request: PathingRequest): Boolean = !(active && this.request === request)

	fun SafeContext.start() {
		assertClientThread()
		if (request.rotationConfig.rotationMode == RotationMode.Silent) {
			return fail(
				"rotation mode Silent cannot steer movement: it nulls movementYaw, " +
						"so the body would follow the camera instead of the plan. Use Sync or Lock.",
			)
		}
		planTrajectory()
	}

	internal val steersRotation: Boolean get() = active && (replay != null || state is State.Aligning)

	fun SafeContext.tick() {
		assertClientThread()
		if (!active) return
		advanceWorld()
		if (!active) return
		with(heartbeat) { beat(this@PathingSession) }
		if (mc.isPaused) return
		if (performPending) {
			performPending = false
			perform()
		}
		if (state is State.Rehearsing && rehearsal?.performing != true) return
		if (state is State.Settling) return with(alignment) { settle() }
		if (state is State.Planning) {
			if (afloatStart != null) tickInput = treadInput()
			return
		}
		if (state is State.Aligning) return with(alignment) { align() }

		waypointRoute.primeNextLeg(player, request, replay?.path)
		with(execution) { tickExecution() }
	}

	fun SafeContext.inputForThisTick(): MovementSimulationInput? {
		assertClientThread()
		val input = tickInput?.takeIf { active } ?: return null
		FlightPermissionHold.hold()

		val replay = replay
		if (replay != null && replay.cursor.awaitingObservation) {
			val path = replay.path
			val frame = replay.cursor.nextFrame
			with(admission) { executionEnvironmentDeviation(path, nextFrame = frame) }?.let { deviation ->
				with(execution) { reject(path, frame, deviation, observe(path.plan, frame), afterInput = false) }
				return null
			}
		}
		return input
	}

	fun SafeContext.useForThisTick() {
		assertClientThread()
		if (!active) return
		val replay = replay?.takeIf { it.cursor.awaitingObservation && !it.holding } ?: return
		val frame = replay.cursor.nextFrame
		val use = replay.path.plan.tape[frame].use ?: return
		if (replay.usedFrame == frame) return
		replay.usedFrame = frame
		with(execution) { use(replay, frame, use) }
	}

	fun cancel() {
		assertClientThread()
		if (active) dumpEvent("cancel", "cancelled at ${Lambda.mc.player?.blockPos} while ${state::class.simpleName}")
		release()
		if (state !is State.Complete && state !is State.Failed) state = State.Idle
	}

	fun onBlockChanged(pos: BlockPos) {
		assertClientThread()
		journey?.world?.onBlockChanged(pos)
	}

	fun onChunkLoaded(chunkX: Int, chunkZ: Int) {
		assertClientThread()
		journey?.world?.onChunkLoaded(chunkX, chunkZ)
	}

	fun onChunkUnloaded(chunkX: Int, chunkZ: Int, sections: Array<ChunkSection>?) {
		assertClientThread()
		journey?.world?.onChunkUnloaded(chunkX, chunkZ, sections)
	}

	internal fun release(park: Boolean = false) {
		launcher.cancel()
		active = false
		val parked = journey?.takeIf { park && request.waypoints.isEmpty() && rehearsal == null }
		if (parked != null) PathingManager.park(parked) else journey?.cancel()
		journey = null
	}

	internal fun SafeContext.retarget(goal: Stance): Boolean {
		if (!active || rehearsal != null || request.waypoints.isNotEmpty()) return false
		if (state is State.Complete || state is State.Failed) return false
		val resolved = resolveGoalStance(player, goal)
		if (resolved == this@PathingSession.goal) return true
		val journey = journey
		if (journey != null && !journey.retarget(resolved)) return false
		this@PathingSession.goal = resolved
		with(launcher) { retargeted() }
		return true
	}

	internal fun faceStart(plan: TrajectoryPlan) =
		face(plan.initialState.rotation.yaw, plan.initialState.rotation.pitch.takeIf { plan.usesBlocks })

	internal fun face(target: Double, pitch: Double? = null) {
		request.runSafeAutomated {
			RotationManager.actingAs(this@PathingSession) {
				if (pitch == null) rotationRequest { yaw(target); exact() }.submit()
				else rotationRequest { rotation(target, pitch); exact() }.submit()
			}
		}
	}

	internal fun takeJourney(): PlanningJourney? = journey.also { journey = null }

	internal fun SafeContext.perform() {
		val rehearsed = rehearsal ?: return
		if (rehearsed.performing) return
		val best = rehearsed.best
		if (best == null) {
			if (!rehearsed.performRequested) info("Walking as soon as the rehearsal spine is ready.", PATHING_SOURCE)
			rehearsed.performRequested = true
			return
		}
		rehearsed.performRequested = true
		if (!with(alignment) { atRest() }) {
			alignment.beginSettling()
			tickInput = ALIGNMENT_INPUT
			state = State.Settling(goalLabel())
			return
		}
		if (!TrajectoryExecutionCursor(best.plan).startsFrom(MovementSimulationState.from(player))) {
			rehearsal = null
			info(
				"Moved %.2f blocks from where the rehearsal starts; planning from here.".format(with(alignment) { startDrift(best) }),
				PATHING_SOURCE,
			)
			return planTrajectory()
		}
		rehearsed.performRequested = false
		rehearsed.performing = true
		with(admission) { begin(best) }
	}

	internal fun SafeContext.startAtRest() {
		if (rehearsal?.let { it.performRequested && it.best != null } == true) return perform()
		with(launcher) { capturePlan() }
	}

	internal fun SafeContext.planTrajectory() {
		if (afloat()) return with(launcher) { capturePlan() }
		if (player.abilities.flying) {
			if (Lambda.mc.interactionManager?.isFlyingLocked == true) return fail("cannot walk while flight is locked")
			player.abilities.flying = false
			player.sendAbilitiesUpdate()
		}
		if (!with(alignment) { atRest() }) {
			alignment.beginSettling()
			tickInput = ALIGNMENT_INPUT
			state = State.Settling(goalLabel())
			return
		}
		with(launcher) { capturePlan() }
	}

	internal fun advanceWorld() {
		if (state is State.Planning) alignment.holdPlanningYaw()
		if (!active) return
		journey?.world?.advance()
		waypointRoute.advanceWorld()
	}

	internal fun dumpEvent(kind: String, note: String, execution: PlanDump.ExecutionTrace? = replayTrace()) {
		val now = System.currentTimeMillis()
		if (now - lastEventDump < EVENT_DUMP_INTERVAL_MILLIS) return
		val job = launcher.current?.job ?: launcher.lastJob ?: return
		lastEventDump = now
		job.dumpEvent(kind, note, execution)
	}

	private fun replayTrace(): PlanDump.ExecutionTrace? {
		val running = replay ?: return null
		val observed = Lambda.mc.player?.let { MovementSimulationState.from(it) }
		return PlanDump.ExecutionTrace.of(running.path.plan, running.cursor.nextFrame, afterInput = false, observed)
	}

	private var lastEventDump = 0L

	internal fun SafeContext.fail(reason: String) {
		if (active) dumpEvent("stopped", reason)
		val goal = if (active) this@PathingSession.goal else telemetry.published?.finalGoal
		val position = player.blockPos.let { "(${it.x}, ${it.y}, ${it.z})" }
		val destination = goal?.let { " toward $it" } ?: ""
		val walked = if (active) leg else 0
		if (waypointRoute.queuedWaypoints > 0) {
			warn("Dropping ${waypointRoute.queuedWaypoints} queued route waypoint(s): this leg failed.", PATHING_SOURCE)
		}
		waypointRoute.drop()
		release()
		state = State.Failed(reason)
		warn("Stopped ${if (walked == 0) "before" else "during"} continuous replay at $position$destination: $reason", PATHING_SOURCE)
	}

	internal fun SafeContext.recoverOrFail(reason: String) {
		if (recoveredAt?.let { player.pos.distanceTo(it) <= RECOVERY_PROGRESS_BLOCKS } != true) recoveryStreak = 0
		if (recoveryStreak >= MAX_RECOVERY_STREAK) return fail(reason)
		recoveryStreak++
		recoveredAt = player.pos
		recover(reason, reuseCoarseState = true, resumeRoute = true)
	}

	internal fun SafeContext.recover(reason: String, reuseCoarseState: Boolean, resumeRoute: Boolean = false) {
		if (!active) return fail(reason)
		rehearsal = rehearsal?.takeIf { it.performing && (replay?.cursor?.nextFrame ?: 0) == 0 && it.restarts < MAX_REHEARSAL_RESTARTS }?.also {
			it.restarts++
			it.performing = false
			it.performRequested = true
		}
		if (!reuseCoarseState) {
			journey?.cancel()
			journey = null
		}
		launcher.reset()
		launcher.resumeRoute = resumeRoute && reuseCoarseState
		replay = null
		alignment.reset()
		tickInput = ALIGNMENT_INPUT
		telemetry.countRecovery()
		state = State.Settling(goalLabel())
		warn("Replanning after the certified environment changed: $reason", PATHING_SOURCE)
	}

	internal fun continueRoute() {
		waypointRoute.continueNext()
	}

	internal fun goalLabel(): String = goal.toString()

	private var liveProfileAge = Int.MIN_VALUE
	private var liveProfileCache: PlayerPhysicsProfile? = null

	internal fun SafeContext.liveProfile(): PlayerPhysicsProfile {
		val cached = liveProfileCache
		if (cached != null && liveProfileAge == player.age) return cached
		return PlayerPhysicsProfile.capture(player).planned(request.pathingConfig).also {
			liveProfileCache = it
			liveProfileAge = player.age
		}
	}

	private fun assertClientThread() {
		check(Lambda.mc.isOnThread) { "PathingSession must be driven from the client thread" }
	}

	internal fun SafeContext.afloat(): Boolean = player.isTouchingWater && !player.isOnGround

	internal fun SafeContext.airborne(): Boolean = !player.isOnGround && !player.isTouchingWater && !player.isClimbing

	internal fun SafeContext.treadInput() = MovementSimulationInput(jump = player.isTouchingWater)

	internal class AfloatStart(val state: MovementSimulationState, val age: Int)

	internal companion object {
		const val MAX_RECOVERY_STREAK = 8

		const val EVENT_DUMP_INTERVAL_MILLIS = 2_000L

		const val RECOVERY_PROGRESS_BLOCKS = 16.0

		const val MAX_REHEARSAL_RESTARTS = 2

		const val PATHING_SOURCE = "Pathing"

		val ALIGNMENT_INPUT = MovementSimulationInput()

		val TREAD_WATER_INPUT = MovementSimulationInput(jump = true)
	}
}
