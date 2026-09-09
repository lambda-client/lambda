package com.lambda.pathing.session

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.PlanningPreparationResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.TrajectoryPlanningPreparation
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.session.PathingSession.Companion.ALIGNMENT_INPUT
import com.lambda.pathing.session.PathingSession.Companion.MAX_SESSION_RESTARTS
import com.lambda.pathing.session.PathingSession.Companion.PATHING_SOURCE
import com.lambda.pathing.session.PathingSession.State
import com.lambda.pathing.world.InterestPrimer
import com.lambda.pathing.world.PathingWorld
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.player.MovementUtils.moveYaw

internal class PlanningLauncher(private val walk: PathingSession) {

	fun SafeContext.capturePlan() {
		walk.tickInput = ALIGNMENT_INPUT

		walk.cancelPlanning()
		walk.cancelSuccessor()
		walk.holding = false
		val request = walk.request
		val session = PlanningSession(++walk.planningGeneration, request)
		walk.planningSession = session

		walk.planningYaw = player.moveYaw.toDouble()
		skipWaypointsUnderfoot()
		walk.state = State.Planning(walk.goalLabel())
		PlanningDebugChannel.begin(
			PlanningDebugChannel.hudWanted || request.pathingRenderConfig.enabled &&
					(request.pathingRenderConfig.renderPlanning || request.pathingRenderConfig.renderGraph),
			PlanningDebugChannel.GraphViewLimits(
				radius = request.pathingRenderConfig.graphRadius,
				cells = request.pathingRenderConfig.graphCellBudget,
				edges = request.pathingRenderConfig.graphEdgeBudget,
			),
		)

		val preparation = when (val prepared = TrajectoryPlanner.prepare(
			player = player,
			goal = request.goal,
			config = request.pathingConfig,
			turnSpeed = request.rotationConfig.turnSpeed,
			cancellation = session.cancellation,
			waypoints = walk.remainingWaypoints(),
		)) {
			is PlanningPreparationResult.Ready -> prepared.preparation
			is PlanningPreparationResult.Failed -> return with(walk) { fail(prepared.failure.message) }
			PlanningPreparationResult.Cancelled -> return with(walk) { fail("planning was cancelled") }
		}
		val currentJourney = walk.journey?.takeIf { it.compatibleWith(preparation) } ?: run {
			walk.journey?.cancel()
			val pathingWorld = PathingWorld(preparation.bounds, player.entityWorld, player)
			InterestPrimer.primeJourney(pathingWorld, preparation.start, preparation.finalGoal, preparation.waypoints)
			PlanningJourney(
				goal = preparation.finalGoal,
				waypoints = preparation.waypoints,
				moveOptions = preparation.moveOptions,
				profile = preparation.profile,
				cancellation = PlanningCancellation(),
				world = pathingWorld,
				legs = TrajectoryPlanner.journeyLegs(
					preparation, pathingWorld.snapshot, pathingWorld::chunkCapturable,
				),
			).also { walk.journey = it }
		}
		launch(
			session, preparation, currentJourney,
			cursorFrame = session::executionFrame,
			adoptedSequence = { session.adoptedSequence },
			onImprovement = { improvement ->
				if (walk.active && walk.planningSession === session) {
					if (walk.cursor != null) with(walk.admission) { adopt(improvement) }
				}
			},
			onSafePrefix = { prefix ->
				if (walk.active && walk.planningSession === session) {
					if (walk.cursor == null && walk.pendingPath == null) with(walk.admission) { begin(prefix) }
				}
			},
			onLaunchFailure = { failure ->
				with(walk) { fail("could not start trajectory planning: ${failure.message}") }
			},
			onComplete = { result, failure -> completePlanning(session, result, failure) },
		)
		with(walk) { advanceSnapshotCapture() }
	}

	private fun SafeContext.skipWaypointsUnderfoot() {
		val waypoints = walk.request.waypoints
		while (walk.passedWaypoints < waypoints.size) {
			val waypoint = TrajectoryPlanner.resolveGoalStance(player, waypoints[walk.passedWaypoints])
			val horizontal = kotlin.math.hypot(player.pos.x - (waypoint.x + 0.5), player.pos.z - (waypoint.z + 0.5))
			if (horizontal > WAYPOINT_UNDERFOOT_BLOCKS || kotlin.math.abs(player.pos.y - waypoint.y) > 1.0) break
			walk.passedWaypoints++
			walk.leg++
			info("Standing at waypoint $waypoint; continuing past it.", PATHING_SOURCE)
		}
	}

	private fun SafeContext.completePlanning(session: PlanningSession, result: PathPlanResult?, failure: Throwable?) {
		if (!walk.active || walk.planningSession !== session) return
		if (failure != null) {
			LOG.error("Pathing worker failed while expanding the trajectory", failure)
			with(walk) { fail("trajectory expansion crashed: ${failure.rootMessage()}") }
			return
		}
		when (val completed = checkNotNull(result)) {
			is PathPlanResult.Planned ->
				if (walk.cursor != null) with(walk.admission) { adopt(completed.path) }
				else with(walk.admission) { begin(completed.path) }
			is PathPlanResult.Failed ->
				if (walk.cursor != null) {

					walk.sessionFailure = completed.failure.message
					LOG.info(
						"Planning session dead-ended mid-walk ({}); planning the successor now",
						completed.failure.message,
					)
					planSuccessor()
				} else if (walk.sessionRestarts < MAX_SESSION_RESTARTS &&
					walk.journey?.world?.revision != session.launchRevision
				) {

					walk.sessionRestarts++
					walk.planningSession = null
					LOG.info(
						"Planning dead-ended before motion ({}); retrying ({}/{})",
						completed.failure.message, walk.sessionRestarts, MAX_SESSION_RESTARTS,
					)
					with(walk) { planTrajectory() }
				} else with(walk) { fail(completed.failure.message) }
			PathPlanResult.Cancelled -> with(walk) { fail("planning was cancelled") }
		}
		if (walk.planningSession === session) {
			walk.planningSession = null
		}
	}

	fun SafeContext.planSuccessor() {
		if (walk.successorSession != null || walk.successorPath != null) return
		val running = walk.telemetry.published ?: return
		val terminal = running.plan.frames.lastOrNull()?.state ?: return
		val request = walk.request
		val session = PlanningSession(++walk.planningGeneration, request)

		val preparation = when (
			val prepared = TrajectoryPlanner.prepare(
				player = player,
				goal = request.goal,
				config = request.pathingConfig,
				turnSpeed = request.rotationConfig.turnSpeed,
				cancellation = session.cancellation,
				initialOverride = terminal,
				waypoints = walk.remainingWaypoints(),
			)
		) {
			is PlanningPreparationResult.Ready -> prepared.preparation
			is PlanningPreparationResult.Failed -> {
				LOG.info("Successor planning declined: {}", prepared.failure.message)
				return
			}
			PlanningPreparationResult.Cancelled -> return
		}

		val successorJourney = walk.journey?.takeIf { it.compatibleWith(preparation) } ?: return
		walk.successorSession = session

		launch(
			session, preparation, successorJourney,

			cursorFrame = { null },
			adoptedSequence = { Long.MAX_VALUE },
			onImprovement = { improvement ->
				if (walk.active && walk.successorSession === session) {
					walk.successorPath = improvement
				}
			},
			onSafePrefix = { prefix ->
				if (walk.active && walk.successorSession === session) {
					if (walk.successorPath == null) walk.successorPath = prefix
				}
			},
			onLaunchFailure = { failure ->
				LOG.info("Could not start successor planning: {}", failure.message)
				walk.successorSession = null
			},
			onComplete = { result, failure ->
				if (!walk.active || walk.successorSession !== session) return@launch
				if (failure == null && result is PathPlanResult.Planned) {
					walk.successorPath = result.path
				}
			},
		)
	}

	private fun SafeContext.launch(
		session: PlanningSession,
		preparation: TrajectoryPlanningPreparation,
		journey: PlanningJourney,
		cursorFrame: () -> Int?,
		adoptedSequence: () -> Long,
		onImprovement: (PublishedPath) -> Unit,
		onSafePrefix: (PublishedPath) -> Unit,
		onLaunchFailure: (Exception) -> Unit,
		onComplete: (PathPlanResult?, Throwable?) -> Unit,
	) {
		val planning = try {
			TrajectoryPlanner.planAsync(
				preparation = preparation,
				world = journey.world,
				cursorFrame = cursorFrame,
				adoptedSequenceProvider = adoptedSequence,
				onImprovement = { improvement -> mc.execute { onImprovement(improvement) } },
				onSafePrefix = { prefix -> mc.execute { onSafePrefix(prefix) } },
				cancellation = session.cancellation,
				planningGeneration = session.generation,
				snapshotRevision = journey.world.revision,
				legStates = journey.legsFor(preparation),
			)
		} catch (failure: Exception) {
			onLaunchFailure(failure)
			return
		}
		session.launchRevision = journey.world.revision
		session.attach(planning)

		planning.whenCompleteAsync({ result, failure -> onComplete(result, failure) }, mc)
	}

	private fun Throwable.rootMessage(): String {
		var root = this
		while (root.cause != null && root.cause !== root) root = root.cause!!
		return root.message ?: root::class.simpleName ?: "unknown error"
	}

	private companion object {

		const val WAYPOINT_UNDERFOOT_BLOCKS = 1.5
	}
}
