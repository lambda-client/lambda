package com.lambda.pathing.session

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.session.PathingSession.Companion.ALIGNMENT_INPUT
import com.lambda.pathing.session.PathingSession.Companion.PATHING_SOURCE
import com.lambda.pathing.session.PathingSession.State
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.player.MovementUtils.moveYaw
import kotlin.math.abs
import kotlin.math.hypot

internal class PlanningLauncher(private val walk: PathingSession) {
	var current: PlanningSession? = null
		private set

	var restarts = 0
		private set

	var lastJob: PlanningJob? = null
		private set

	private var deadEnd: String? = null
	private var deadEndMobs = false
	private var captureWaits = 0
	private var captureWaitSince = 0L
	private var mobWaitSince = 0L
	private var mobPatienceSince = 0L
	private var mobCells: LongArray? = null
	private var successor: PlanningSession? = null
	private var successorPath: PublishedPath? = null

	fun cancel() {
		val planning = current
		current = null
		planning?.cancel()
		dropSuccessor()
	}

	private fun dropSuccessor() {
		successor?.cancel()
		successor = null
		successorPath = null
	}

	fun reset() {
		cancel()
		deadEnd = null
		deadEndMobs = false
		captureWaitSince = 0L
		mobWaitSince = 0L
		mobPatienceSince = 0L
		mobCells = null
	}

	fun progressed() {
		restarts = 0
		captureWaits = 0
		mobPatienceSince = 0L
	}

	fun SafeContext.retryAfterCapture() {
		if (current != null) return
		if (mobWaitSince != 0L) return retryAfterMobs()
		if (captureWaitSince == 0L) return
		val waited = System.currentTimeMillis() - captureWaitSince
		val pending = walk.journey?.world?.pendingInterest ?: 0
		if (waited < walk.request.pathingConfig.captureRetryMillis || pending > 0 && waited < MAX_CAPTURE_WAIT_MILLIS) return
		captureWaitSince = 0L
		with(walk) { planTrajectory() }
	}

	private fun SafeContext.retryAfterMobs() {
		val waited = System.currentTimeMillis() - mobWaitSince
		val moved = walk.journey?.world?.obstacleCells !== mobCells
		if (waited < MOB_RETRY_MIN_MILLIS || !moved && waited < MOB_RETRY_MAX_MILLIS) return
		mobWaitSince = 0L
		mobCells = null
		with(walk) { planTrajectory() }
	}

	private fun waitForMobs(reason: String): Boolean {
		val now = System.currentTimeMillis()
		if (mobPatienceSince == 0L) mobPatienceSince = now
		if (now - mobPatienceSince > MOB_PATIENCE_MILLIS) return false
		current = null
		mobWaitSince = now
		mobCells = walk.journey?.world?.obstacleCells
		LOG.info("Planning dead-ended before motion with mobs on the route ({}); waiting for them to move", reason)
		return true
	}

	fun SafeContext.capturePlan() {
		walk.tickInput = ALIGNMENT_INPUT

		cancel()
		val request = walk.request
		val session = PlanningSession()
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
		walk.afloatStart = if (with(walk) { afloat() }) PathingSession.AfloatStart(preparation.initial, player.age) else null
		if (walk.afloatStart != null) walk.tickInput = with(walk) { treadInput() }
		val currentJourney = walk.journey?.takeIf { it.compatibleWith(preparation) } ?: run {
			walk.journey?.cancel()
			PlanningJourney.open(preparation, player).also { walk.journey = it }
		}
		launch(session, preparation, currentJourney)
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
					deadEndMobs = completed.mobs
					walk.heartbeat.soon()
					LOG.info(
						"Planning session dead-ended mid-walk ({}); planning the successor now",
						completed.reason,
					)
					planSuccessor()
				} else if (captureWaits < walk.request.pathingConfig.captureRetries && (walk.journey?.world?.pendingInterest ?: 0) > 0) {
					captureWaits++
					current = null
					captureWaitSince = System.currentTimeMillis()
					LOG.info(
						"Planning dead-ended before motion ({}) with {} sections still to capture; retrying once the capture catches up ({}/{})",
						completed.reason, walk.journey?.world?.pendingInterest ?: 0, captureWaits, walk.request.pathingConfig.captureRetries,
					)
				} else if (completed.mobs && waitForMobs(completed.reason)) {
					return
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
		val mobs = deadEndMobs
		deadEnd = null
		deadEndMobs = false
		if (reason != null && !mobs && restarts >= MAX_SESSION_RESTARTS) {
			return with(walk) { fail("planning kept dead-ending: $reason") }
		}
		if (reason != null && !mobs) restarts++
		walk.replay?.takeIf { it.holding }?.let { held ->
			LOG.info(
				"Held {} ms at the tape terminal; the session refused {} offered tape(s){}",
				System.currentTimeMillis() - held.heldSince, held.heldRefusals,
				held.lastHeldRefusal?.let { ", the last because $it" }.orEmpty(),
			)
		}
		walk.replay = null
		walk.tickInput = null

		val next = successor
		val planned = successorPath
		successor = null
		successorPath = null
		if (next != null) {
			current = next
			lastJob = next.job
			next.attach()
			LOG.info("Continuing with the successor search planned during replay")
		}
		when {
			planned != null -> with(walk.admission) { begin(planned) }
			next != null -> walk.state = State.Planning(walk.goalLabel())
			else -> with(walk) { planTrajectory() }
		}
	}

	fun SafeContext.planSuccessor() {
		if (successor != null || successorPath != null) return
		val running = walk.replay?.path ?: return
		val terminal = running.plan.frames.lastOrNull()?.state ?: return
		val request = walk.request

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
		val session = PlanningSession(attached = false)
		successor = session
		launch(session, preparation, successorJourney)
	}

	private fun SafeContext.published(session: PlanningSession, path: PublishedPath, running: Boolean) {
		if (!walk.active) return
		if (session === successor) {
			successorPath = path
			return
		}
		if (session !== current) return
		when {
			walk.replay != null && running -> with(walk.admission) { adopt(path) }
			walk.replay == null && walk.alignment.pendingPath != null -> walk.alignment.beginAligning(path)
			walk.replay == null && !running -> with(walk.admission) { begin(path) }
			else -> session.refuse(path.publicationSequence.toLong())
		}
	}

	private fun SafeContext.completed(session: PlanningSession, result: PathPlanResult?, failure: Throwable?) {
		if (!walk.active) return
		if (session !== successor) return completePlanning(session, result, failure)
		if (failure == null && result is PathPlanResult.Planned) successorPath = result.path
		successor = null
	}

	private fun SafeContext.launch(session: PlanningSession, preparation: PlanningPreparation, journey: PlanningJourney) {
		session.launchRevision = journey.world.revision
		val job = PlanningJob(
			preparation = preparation,
			journey = journey,
			cancelled = { session.isCancelled },
			execution = session,
			publish = { path, running -> mc.execute { published(session, path, running) } },
		)
		session.job = job
		if (session === current) lastJob = job
		job.start().whenCompleteAsync({ result, failure -> completed(session, result, failure) }, mc)
	}

	private fun Throwable.rootMessage(): String {
		var root = this
		while (root.cause != null && root.cause !== root) root = root.cause!!
		return root.message ?: root::class.simpleName ?: "unknown error"
	}

	private companion object {

		const val WAYPOINT_UNDERFOOT_BLOCKS = 1.5

		const val MAX_SESSION_RESTARTS = 3

		const val MAX_CAPTURE_WAIT_MILLIS = 20_000L

		const val MOB_RETRY_MIN_MILLIS = 1_000L

		const val MOB_RETRY_MAX_MILLIS = 3_000L

		const val MOB_PATIENCE_MILLIS = 30_000L
	}
}
