package com.lambda.pathing.session

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.search.ExecutionFeedback
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.session.PathingSession.Companion.ALIGNMENT_INPUT
import com.lambda.pathing.session.PathingSession.Companion.PATHING_SOURCE
import com.lambda.pathing.session.PathingSession.State
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.player.MovementUtils.moveYaw
import kotlin.math.abs
import kotlin.math.hypot

internal class PlanningLauncher(private val walk: PathingSession) {
	private var generation = 0L

	var current: PlanningSession? = null
		private set

	var restarts = 0
		private set

	private var deadEnd: String? = null
	private var successor: PlanningSession? = null
	private var successorPath: PublishedPath? = null

	fun cancel() {
		val planning = current
		current = null
		planning?.cancel()
		dropSuccessor()
	}

	private fun dropSuccessor(): PublishedPath? {
		val planning = successor
		val planned = successorPath
		successor = null
		successorPath = null
		planning?.cancel()
		return planned
	}

	fun reset() {
		cancel()
		deadEnd = null
	}

	fun progressed() {
		restarts = 0
	}

	fun SafeContext.capturePlan() {
		walk.tickInput = ALIGNMENT_INPUT

		cancel()
		val request = walk.request
		val session = PlanningSession(++generation)
		current = session

		walk.alignment.planningYaw = player.moveYaw.toDouble()
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

		val preparation = when (val prepared = preparePlanning(
			player, request, request.goal, walk.remainingWaypoints(),
		)) {
			is PlanningPreparationResult.Ready -> prepared.preparation
			is PlanningPreparationResult.Failed -> return with(walk) { fail(prepared.reason) }
		}
		val currentJourney = walk.journey?.takeIf { it.compatibleWith(preparation) } ?: run {
			walk.journey?.cancel()
			PlanningJourney.open(preparation, player).also { walk.journey = it }
		}
		launch(
			session, preparation, currentJourney,
			execution = session,
			onPublish = { path, running ->
				if (walk.active && current === session) {
					if (running) {
						if (walk.replay != null) with(walk.admission) { adopt(path) }
					} else if (walk.replay == null && walk.alignment.pendingPath == null) {
						with(walk.admission) { begin(path) }
					}
				}
			},
			onComplete = { result, failure -> completePlanning(session, result, failure) },
		)
		with(walk) { advanceSnapshotCapture() }
	}

	private fun SafeContext.skipWaypointsUnderfoot() {
		val waypoints = walk.request.waypoints
		while (walk.passedWaypoints < waypoints.size) {
			val waypoint = resolveGoalStance(player, waypoints[walk.passedWaypoints])
			val horizontal = hypot(player.pos.x - (waypoint.x + 0.5), player.pos.z - (waypoint.z + 0.5))
			if (horizontal > WAYPOINT_UNDERFOOT_BLOCKS || abs(player.pos.y - waypoint.y) > 1.0) break
			walk.passedWaypoints++
			walk.leg++
			info("Standing at waypoint $waypoint; continuing past it.", PATHING_SOURCE)
		}
	}

	private fun SafeContext.completePlanning(session: PlanningSession, result: PathPlanResult?, failure: Throwable?) {
		if (!walk.active || current !== session) return
		if (failure != null) {
			LOG.error("Pathing worker failed while expanding the trajectory", failure)
			with(walk) { fail("trajectory expansion crashed: ${failure.rootMessage()}") }
			return
		}
		when (val completed = checkNotNull(result)) {
			is PathPlanResult.Planned -> with(walk.admission) { adopt(completed.path) }
			is PathPlanResult.Failed ->
				if (walk.replay != null) {
					deadEnd = completed.reason
					LOG.info(
						"Planning session dead-ended mid-walk ({}); planning the successor now",
						completed.reason,
					)
					planSuccessor()
				} else if (restarts < MAX_SESSION_RESTARTS &&
					walk.journey?.world?.revision != session.launchRevision
				) {
					restarts++
					current = null
					LOG.info(
						"Planning dead-ended before motion ({}); retrying ({}/{})",
						completed.reason, restarts, MAX_SESSION_RESTARTS,
					)
					with(walk) { planTrajectory() }
				} else with(walk) { fail(completed.reason) }
			PathPlanResult.Cancelled -> with(walk) { fail("planning was cancelled") }
		}
		if (current === session) {
			current = null
		}
	}

	fun SafeContext.resumeAfterHold() {
		val reason = deadEnd
		deadEnd = null
		if (reason != null && restarts >= MAX_SESSION_RESTARTS) {
			return with(walk) { fail("planning kept dead-ending: $reason") }
		}
		if (reason != null) restarts++
		walk.replay = null
		walk.tickInput = null

		val planned = dropSuccessor() ?: return with(walk) { planTrajectory() }
		LOG.info("Installing the successor tape planned during replay")
		with(walk.admission) { begin(planned) }
	}

	fun SafeContext.planSuccessor() {
		if (successor != null || successorPath != null) return
		val running = walk.replay?.path ?: return
		val terminal = running.plan.frames.lastOrNull()?.state ?: return
		val request = walk.request
		val session = PlanningSession(++generation)

		val preparation = when (
			val prepared = preparePlanning(
				player, request, request.goal, walk.remainingWaypoints(), initialOverride = terminal,
			)
		) {
			is PlanningPreparationResult.Ready -> prepared.preparation
			is PlanningPreparationResult.Failed -> {
				LOG.info("Successor planning declined: {}", prepared.reason)
				return
			}
		}

		val successorJourney = walk.journey?.takeIf { it.compatibleWith(preparation) } ?: return
		successor = session

		launch(
			session, preparation, successorJourney,
			execution = ExecutionFeedback.DETACHED,
			onPublish = { path, _ ->
				if (walk.active && successor === session) successorPath = path
			},
			onComplete = { result, failure ->
				if (!walk.active || successor !== session) return@launch
				if (failure == null && result is PathPlanResult.Planned) {
					successorPath = result.path
				}
			},
		)
	}

	private fun SafeContext.launch(
		session: PlanningSession,
		preparation: PlanningPreparation,
		journey: PlanningJourney,
		execution: ExecutionFeedback,
		onPublish: (PublishedPath, Boolean) -> Unit,
		onComplete: (PathPlanResult?, Throwable?) -> Unit,
	) {
		session.launchRevision = journey.world.revision
		PlanningJob(
			preparation = preparation,
			world = journey.world,
			legStates = journey.legsFor(preparation),
			cancelled = { session.isCancelled },
			planningGeneration = session.generation,
			snapshotRevision = session.launchRevision,
			execution = execution,
			publish = { path, running -> mc.execute { onPublish(path, running) } },
		).start().whenCompleteAsync({ result, failure -> onComplete(result, failure) }, mc)
	}

	private fun Throwable.rootMessage(): String {
		var root = this
		while (root.cause != null && root.cause !== root) root = root.cause!!
		return root.message ?: root::class.simpleName ?: "unknown error"
	}

	private companion object {

		const val WAYPOINT_UNDERFOOT_BLOCKS = 1.5

		const val MAX_SESSION_RESTARTS = 3
	}
}
