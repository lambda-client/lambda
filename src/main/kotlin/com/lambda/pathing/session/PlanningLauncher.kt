package com.lambda.pathing.session

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.pathing.core.Stance
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

	var resumeRoute = false

	private var deadEnd: String? = null
	private var deadEndMobs = false
	private var throughMobsUntil = 0L
	private var successor: PlanningSession? = null
	private var successorPath: PublishedPath? = null
	private var successorJoin = -1

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
		successorJoin = -1
	}

	fun SafeContext.joinBridge(replay: Replay): Boolean {
		val frame = replay.cursor.nextFrame
		if (successorJoin >= 0 && frame >= successorJoin) {
			val next = successor
			val path = successorPath
			val joins = frame == successorJoin && path != null && path.plan.initialState == joinState(replay, frame)
			if (!joins) dropSuccessor() else {
				successor = null
				successorPath = null
				successorJoin = -1
				if (next != null) {
					current = next
					lastJob = next.job
					next.attach()
				}
				with(walk.admission) { join(path) }
				return true
			}
		}
		val join = frame + JOIN_LEAD_FRAMES
		if (successorJoin < 0 && frame >= BRIDGE_SETTLE_FRAMES && (frame - BRIDGE_SETTLE_FRAMES) % JOIN_LEAD_FRAMES == 0 && join < replay.path.plan.tape.frameCount) {
			planJoin(replay, join)
		}
		return false
	}

	private fun joinState(replay: Replay, frame: Int) = replay.path.plan.frames[frame - 1].state.copy(isJumping = false)

	private fun SafeContext.planJoin(replay: Replay, join: Int) {
		val prepared = preparePlanning(player, walk.request, walk.goal, walk.remainingWaypoints(), initialOverride = joinState(replay, join))
		val preparation = (prepared as? PlanningPreparationResult.Ready)?.preparation ?: return
		val journey = walk.journey?.takeIf { it.compatibleWith(preparation) } ?: return
		dropSuccessor()
		val session = PlanningSession(attached = false)
		successor = session
		successorJoin = join
		launch(session, preparation, journey, resumed = true)
	}

	fun reset() {
		cancel()
		deadEnd = null
		deadEndMobs = false
	}

	fun progressed() {
		restarts = 0
	}

	fun SafeContext.capturePlan() {
		walk.tickInput = ALIGNMENT_INPUT

		cancel()
		val resumed = resumeRoute.also { resumeRoute = false }
		val request = walk.request
		val session = PlanningSession()
		current = session

		walk.alignment.planningYaw = player.moveYaw.toDouble()
		rewindToWaypointUnderfoot()
		skipWaypointsUnderfoot()
		walk.state = walk.rehearsal?.let { State.Rehearsing(walk.goalLabel(), it.history.size, it.bestFrames, finished = false) } ?: State.Planning(walk.goalLabel())
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
			player, request, walk.goal, walk.remainingWaypoints(),
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
		launch(session, preparation, currentJourney, resumed)
		walk.advanceWorld()
	}

	private fun SafeContext.rewindToWaypointUnderfoot() {
		val waypoints = walk.request.waypoints
		for (index in walk.passedWaypoints - 2 downTo 0) {
			val waypoint = resolveGoalStance(player, waypoints[index])
			if (!underfoot(waypoint)) continue
			walk.leg -= walk.passedWaypoints - (index + 1)
			walk.passedWaypoints = index + 1
			info("Back at waypoint $waypoint; ${walk.remainingWaypoints().size} waypoint(s) again before ${walk.request.goal}.", PATHING_SOURCE)
			return
		}
	}

	private fun SafeContext.skipWaypointsUnderfoot() {
		val waypoints = walk.request.waypoints
		while (walk.passedWaypoints < waypoints.size) {
			val waypoint = resolveGoalStance(player, waypoints[walk.passedWaypoints])
			if (!underfoot(waypoint)) break
			walk.passedWaypoints++
			walk.leg++
			info("Standing at waypoint $waypoint; continuing past it.", PATHING_SOURCE)
		}
	}

	private fun SafeContext.underfoot(waypoint: Stance): Boolean =
		hypot(player.pos.x - (waypoint.x + 0.5), player.pos.z - (waypoint.z + 0.5)) <= WAYPOINT_UNDERFOOT_BLOCKS && abs(player.pos.y - waypoint.y) <= 1.0

	private fun SafeContext.completePlanning(session: PlanningSession, result: PathPlanResult?, failure: Throwable?) {
		if (!walk.active || current !== session) return
		if (failure != null) {
			LOG.error("Pathing worker failed while expanding the trajectory", failure)
			with(walk) { fail("trajectory expansion crashed: ${failure.rootMessage()}") }
			return
		}
		walk.rehearsal?.let { rehearsal ->
			current = null
			if (rehearsal.performing) return
			return concludeRehearsal(rehearsal, checkNotNull(result))
		}
		when (val completed = checkNotNull(result)) {
			is PathPlanResult.Planned -> with(walk.admission) { adopt(completed.path) }
			is PathPlanResult.Failed -> {
				if (completed.mobs) throughMobsUntil = System.currentTimeMillis() + THROUGH_MOBS_MILLIS
				if (walk.replay != null) {
					deadEnd = completed.reason
					deadEndMobs = completed.mobs
					walk.heartbeat.soon()
					LOG.info(
						"Planning session dead-ended mid-walk ({}); planning the successor now",
						completed.reason,
					)
					planSuccessor()
				} else if (completed.mobs) {
					current = null
					resumeRoute = true
					LOG.info("Planning dead-ended before motion with mobs on the route ({}); planning through them", completed.reason)
					with(walk) { planTrajectory() }
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
			}
			PathPlanResult.Cancelled -> with(walk) { fail("planning was cancelled") }
		}
		if (current === session) {
			current = null
		}
	}

	private fun SafeContext.concludeRehearsal(rehearsal: Rehearsal, result: PathPlanResult) {
		rehearsal.finished = true
		val best = rehearsal.best
		if (best == null) {
			val reason = (result as? PathPlanResult.Failed)?.reason ?: "the rehearsal found no complete tape"
			return with(walk) { fail(reason) }
		}
		rehearsal.progress = "converged"
		walk.state = State.Rehearsing(walk.goalLabel(), rehearsal.history.size, best.plan.tape.frameCount, finished = true)
		val spine = rehearsal.history.firstOrNull()?.frames ?: best.plan.tape.frameCount
		info("Plan ready: ${best.plan.tape.frameCount} frames (spine $spine); `path go` walks it.", PATHING_SOURCE)
		if (rehearsal.performRequested) with(walk) { perform() }
	}

	private fun SafeContext.rehearsed(session: PlanningSession, point: RehearsalPoint, path: PublishedPath) {
		val rehearsal = walk.rehearsal
		if (!walk.active || session !== current || rehearsal == null) return
		if (!rehearsal.record(point, path) || rehearsal.performing) return
		walk.telemetry.published = path
		walk.state = State.Rehearsing(walk.goalLabel(), rehearsal.history.size, path.plan.tape.frameCount, finished = false)
		if (rehearsal.history.size == 1) {
			info("Rehearsal spine: ${path.plan.tape.frameCount} frames in %.1f s; refining, `path go` walks the best plan so far.".format(point.millis / 1000.0), PATHING_SOURCE)
		}
		if (rehearsal.performRequested) with(walk) { perform() }
	}

	private fun rehearsalProgress(session: PlanningSession, progress: String) {
		if (session !== current) return
		walk.rehearsal?.progress = progress
	}

	fun SafeContext.resumeAfterHold() {
		if (successorJoin >= 0) dropSuccessor()
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

	fun SafeContext.retargeted() {
		dropSuccessor()
		if (current == null && walk.replay != null) planSuccessor()
	}

	fun SafeContext.planSuccessor() {
		if (successor != null || successorPath != null) return
		val running = walk.replay?.path ?: return
		val terminal = running.plan.frames.lastOrNull()?.state ?: return
		val request = walk.request

		val preparation = when (
			val prepared = preparePlanning(
				player, request, walk.goal, walk.remainingWaypoints(), initialOverride = terminal,
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
		launch(session, preparation, successorJourney, resumed = true)
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

	private fun SafeContext.launch(session: PlanningSession, preparation: PlanningPreparation, journey: PlanningJourney, resumed: Boolean) {
		session.launchRevision = journey.world.revision
		val job = PlanningJob(
			preparation = preparation,
			journey = journey,
			cancelled = { session.isCancelled },
			execution = session,
			publish = { path, running -> mc.execute { published(session, path, running) } },
			rehearsal = walk.rehearsal.takeIf { session === current },
			onRehearsal = { point, path -> mc.execute { rehearsed(session, point, path) } },
			onRehearsalProgress = { progress -> rehearsalProgress(session, progress) },
			resumed = resumed,
			throughMobs = System.currentTimeMillis() < throughMobsUntil,
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

		const val THROUGH_MOBS_MILLIS = 5_000L

		const val JOIN_LEAD_FRAMES = 10

		const val BRIDGE_SETTLE_FRAMES = 2
	}
}
