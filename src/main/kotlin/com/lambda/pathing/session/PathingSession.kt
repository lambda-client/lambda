package com.lambda.pathing.session

import com.lambda.Lambda
import com.lambda.context.SafeContext
import com.lambda.interaction.managers.rotating.RotationMode
import com.lambda.pathing.PathingRequest
import com.lambda.pathing.WaypointRoute
import com.lambda.pathing.core.Stance
import com.lambda.pathing.execution.FlightPermissionHold
import com.lambda.pathing.execution.TrajectoryExecutionCursor
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.session.AlignmentController.Companion.SETTLED_SPEED
import com.lambda.pathing.world.InterestPrimer
import com.lambda.util.CommunicationUtils.warn
import net.minecraft.util.math.BlockPos

class PathingSession internal constructor(
	internal val request: PathingRequest,

	internal var journey: PlanningJourney?,
	private val waypointRoute: WaypointRoute,
) {
	sealed interface State {
		data object Idle : State

		data class Settling(val goal: String) : State
		data class Planning(val goal: String) : State
		data class Aligning(val leg: Int, val yawError: Double) : State
		data class Executing(val frame: Int, val frames: Int, val leg: Int) : State

		data class Complete(val frames: Int, val legs: Int) : State
		data class Failed(val reason: String) : State
	}

	@Volatile
	var state: State = State.Idle
		internal set

	internal val telemetry = WalkTelemetry()

	internal var active = true
		private set

	internal var planningGeneration = 0L

	internal val flight = FlightPermissionHold()

	internal var planningSession: PlanningSession? = null

	internal var leg = 0

	internal var passedWaypoints = 0

	internal fun remainingWaypoints(): List<Stance> = request.waypoints.drop(passedWaypoints)

	internal var planningYaw: Double? = null

	internal var pendingPath: PublishedPath? = null
	internal var alignmentTicks = 0
	internal var settleTicks = 0
	internal var cursor: TrajectoryExecutionCursor? = null

	internal var tickInput: MovementSimulationInput? = null
	internal var awaitingObservation = false

	internal var pendingImprovement: PublishedPath? = null

	internal var holding = false
	internal var holds = 0

	internal var sessionRestarts = 0
	internal var sessionFailure: String? = null

	internal var repairDeadline: Int? = null
	internal var repairDeviation: com.lambda.pathing.execution.ExecutionDeviation? = null
	internal var repairs = 0

	internal var successorSession: PlanningSession? = null
	internal var successorPath: PublishedPath? = null

	private val adoptionGains = ArrayList<Int>()
	private val adoptionMillis = ArrayList<Long>()
	private var lastAdoptionMillis = System.currentTimeMillis()
	private var lastAdoptedFrames = 0

	internal val alignment = AlignmentController(this)
	internal val launcher = PlanningLauncher(this)
	internal val admission = TapeAdmission(this)
	internal val execution = ExecutionDriver(this)

	fun telemetry(): Telemetry = telemetry.snapshot().copy(
		holds = holds,
		repairs = repairs,
		leg = leg,
		queuedWaypoints = remainingWaypoints().size + waypointRoute.queuedWaypoints,
		sessionRestarts = sessionRestarts,
		cadence = publicationCadence(),
	)

	fun isFinished(request: PathingRequest): Boolean =
		!(active && this.request === request) || state is State.Complete || state is State.Failed

	fun SafeContext.start() {
		assertClientThread()
		unsteerable(request)?.let { return fail(it, request.goal) }
		planTrajectory()
	}

	fun SafeContext.tick() {
		assertClientThread()
		if (!active) return
		advanceSnapshotCapture()
		if (!active) return
		if (state is State.Settling) return with(alignment) { settle() }
		if (state is State.Planning) return
		if (state is State.Aligning) return with(alignment) { align() }

		waypointRoute.primeNextLeg(player, request, replaying = cursor != null, published = telemetry.published)
		with(execution) { tickExecution() }
	}

	fun SafeContext.inputForThisTick(): MovementSimulationInput? {
		assertClientThread()
		if (!active) {
			flight.release()
			return null
		}
		val input = tickInput ?: run {
			flight.release()
			return null
		}
		flight.hold()

		if (awaitingObservation) {
			val path = telemetry.published ?: run {
				fail("lost the certified plan before input application")
				return null
			}
			val active = cursor ?: run {
				fail("lost the execution cursor before input application")
				return null
			}
			with(admission) { executionEnvironmentDeviation(path, nextFrame = active.nextFrame) }?.let { deviation ->
				with(execution) {
					val observed = observe(path.plan, active.nextFrame)
					reject(active.nextFrame, deviation, observed, afterInput = false)
				}
				return null
			}
		}
		return input
	}

	fun cancel() {
		assertClientThread()
		release()
		if (state is State.Settling || state is State.Planning ||
			state is State.Aligning || state is State.Executing
		) {
			state = State.Idle
		}
	}

	fun onBlockChanged(pos: BlockPos) {
		assertClientThread()
		journey?.world?.onBlockChanged(pos)
	}

	fun onChunkEvent(chunkX: Int, chunkZ: Int) {
		assertClientThread()
		journey?.world?.onChunkEvent(chunkX, chunkZ)
	}

	internal fun release() {
		flight.release()
		cancelPlanning()
		cancelSuccessor()
		active = false
	}

	internal fun finish() {
		active = false
		flight.release()
	}

	internal fun takeJourney(): PlanningJourney? {
		val taken = journey
		journey = null
		return taken
	}

	internal fun SafeContext.planTrajectory() {
		if (player.velocity.horizontalLength() > SETTLED_SPEED) {
			settleTicks = 0
			tickInput = ALIGNMENT_INPUT
			state = State.Settling(goalLabel())
			return
		}
		with(launcher) { capturePlan() }
	}

	internal fun SafeContext.advanceSnapshotCapture() {
		if (state is State.Planning) alignment.holdPlanningYaw()
		if (!active) return
		val activeJourney = journey ?: return

		InterestPrimer.primeBody(activeJourney.world, player.blockPos)
		val configured = request.pathingConfig.snapshotCaptureBudgetMillis

		val budget = if (cursor == null || holding) {
			maxOf(configured, IDLE_CAPTURE_BUDGET_MILLIS)
		} else configured
		activeJourney.world.advance(budget)

		waypointRoute.advanceCapture(configured)
	}

	internal fun SafeContext.fail(
		reason: String,
		goal: Stance? = if (active) request.goal else telemetry.published?.finalGoal,
	) {
		val position = player.blockPos?.let { "(${it.x}, ${it.y}, ${it.z})" } ?: "unknown position"
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

	internal fun SafeContext.recover(reason: String, reuseCoarseState: Boolean = true) {
		if (!active) return fail(reason)
		if (!reuseCoarseState) {
			journey?.cancel()
			journey = null
		}
		resetForReplan()
		tickInput = ALIGNMENT_INPUT
		telemetry.countRecovery()
		state = State.Settling(goalLabel())
		warn("Replanning after the certified environment changed: $reason", PATHING_SOURCE)
	}

	internal fun continueRoute() {
		waypointRoute.continueNext()
	}

	internal fun goalLabel(): String = request.goal.let { "(${it.x}, ${it.y}, ${it.z})" }

	internal fun recordAdoption(tapeFrames: Int) {
		val now = System.currentTimeMillis()
		adoptionGains += (tapeFrames - lastAdoptedFrames).coerceAtLeast(0)
		adoptionMillis += (now - lastAdoptionMillis).coerceAtLeast(0)
		lastAdoptedFrames = tapeFrames
		lastAdoptionMillis = now
	}

	internal fun publicationCadence(): String {
		if (adoptionGains.isEmpty()) return "no adoptions"
		val frames = adoptionGains.sum()
		val millis = adoptionMillis.sum().coerceAtLeast(1L)
		return "%d adoption(s) added %d frames over %d ms (%.1f frames/s produced vs %d consumed)"
			.format(adoptionGains.size, frames, millis, frames * 1000.0 / millis, TICKS_PER_SECOND)
	}

	internal fun cancelPlanning() {
		val planning = planningSession
		planningSession = null
		planning?.cancel()
	}

	internal fun cancelSuccessor() {
		val successor = successorSession
		successorSession = null
		successorPath = null
		successor?.cancel()
	}

	internal fun resetForReplan() {
		cancelPlanning()
		cancelSuccessor()
		cursor = null
		awaitingObservation = false
		pendingPath = null
		pendingImprovement = null
		holding = false
		sessionFailure = null
		repairDeadline = null
		repairDeviation = null
		planningYaw = null
		alignmentTicks = 0
		settleTicks = 0
	}

	private var liveProfileAge = Int.MIN_VALUE
	private var liveProfileCache: PlayerPhysicsProfile? = null

	internal fun SafeContext.liveProfile(): PlayerPhysicsProfile {
		val cached = liveProfileCache
		if (cached != null && liveProfileAge == player.age) return cached
		return PlayerPhysicsProfile.capture(player).also {
			liveProfileCache = it
			liveProfileAge = player.age
		}
	}

	private fun unsteerable(request: PathingRequest): String? {
		val config = request.rotationConfig
		if (config.rotationMode == RotationMode.Silent) {
			return "rotation mode Silent cannot steer movement: it nulls movementYaw, " +
					"so the body would follow the camera instead of the plan. Use Sync or Lock."
		}
		return null
	}

	private fun assertClientThread() {
		check(Lambda.mc.isOnThread) { "PathingSession must be driven from the client thread" }
	}

	internal companion object {
		const val IDLE_CAPTURE_BUDGET_MILLIS = 15.0

		const val MAX_STATE_RECOVERIES = 8

		const val MAX_SESSION_RESTARTS = 3

		const val PATHING_SOURCE = "Pathing"

		val ALIGNMENT_INPUT = MovementSimulationInput()

		private const val TICKS_PER_SECOND = 20
	}
}
