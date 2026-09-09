package com.lambda.pathing.session

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.debug.executionRejectionReport
import com.lambda.pathing.execution.ExecutionDeviation
import com.lambda.pathing.execution.ExecutionInputResult
import com.lambda.pathing.execution.ExecutionObservationResult
import com.lambda.pathing.execution.TrajectoryExecutionCursor
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.search.PlanGraph
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.search.TrajectoryPlan
import com.lambda.pathing.session.PathingSession.Companion.ALIGNMENT_INPUT
import com.lambda.pathing.session.PathingSession.Companion.MAX_SESSION_RESTARTS
import com.lambda.pathing.session.PathingSession.Companion.MAX_STATE_RECOVERIES
import com.lambda.pathing.session.PathingSession.Companion.PATHING_SOURCE
import com.lambda.pathing.session.PathingSession.State
import com.lambda.threading.runSafeAutomated
import com.lambda.util.CommunicationUtils.info

internal class ExecutionDriver(private val walk: PathingSession) {
	private val admission get() = walk.admission

	fun SafeContext.tickExecution() {
		val active = walk.cursor ?: return
		val path = walk.telemetry.published ?: return

		walk.planningSession?.updateExecutionFrame(active.nextFrame)

		val frame = active.nextFrame

		if (walk.awaitingObservation && !finishObservedTick(active, path, frame)) return

		walk.pendingImprovement?.let { improvement ->
			walk.pendingImprovement = null
			with(admission) { adopt(improvement) }
		}

		walk.cursor?.let { cursor ->
			val running = walk.telemetry.published
			if (running != null && !running.partial && !walk.awaitingObservation &&
				cursor.nextFrame >= running.plan.stationaryFrom &&
				cursor.nextFrame < running.plan.tape.frameCount
			) {
				finishTrajectory(running)
				return
			}
		}

		if (walk.holding) {
			walk.telemetry.published?.let { enterHold(it) }
			return
		}

		val current = walk.telemetry.published ?: return
		val running = walk.cursor ?: return

		if (running.nextFrame == 0) {
			if (with(walk.alignment) { realignBeforeLaunch(current) }) return
		}

		val observed = observe(current.plan, running.nextFrame)
		announcePassedWaypoints(current, running.nextFrame)
		walk.repairDeadline?.let { deadline ->
			if (running.nextFrame >= deadline) {

				val deviation = walk.repairDeviation ?: ExecutionDeviation.Protocol("repair deadline reached")
				return reject(running.nextFrame, deviation, observed, afterInput = false)
			}
		}
		with(admission) {
			executionEnvironmentDeviation(current, nextFrame = running.nextFrame, untilFrame = repairUntil(current))
		}?.let { deviation ->
			return handleDeviation(current, running.nextFrame, deviation, observed, afterInput = false)
		}
		when (val next = running.nextInput(observed)) {
			is ExecutionInputResult.Apply -> {
				walk.tickInput = next.input
				walk.awaitingObservation = true
				walk.state = State.Executing(next.frame, current.plan.tape.frameCount, walk.leg)

				next.input.rotation?.let { rotation ->
					walk.request.runSafeAutomated { rotationRequest { yaw(rotation.yaw) }.submit() }
				}
			}

			ExecutionInputResult.Complete -> finishTrajectory(current)

			is ExecutionInputResult.Rejected -> reject(
				next.frame, next.deviation, observed, afterInput = false,
			)
		}
	}

	private fun SafeContext.finishObservedTick(
		active: TrajectoryExecutionCursor,
		path: PublishedPath,
		frame: Int,
	): Boolean {
		val observed = observe(path.plan, frame + 1)
		with(admission) {
			executionEnvironmentDeviation(path, nextFrame = frame, untilFrame = repairUntil(path))
		}?.let { deviation ->
			handleDeviation(path, frame, deviation, observed, afterInput = true)
			return false
		}
		val result = active.observeAfterTick(observed)
		if (result is ExecutionObservationResult.Rejected) {
			reject(result.frame, result.deviation, observed, afterInput = true)
			return false
		}
		walk.awaitingObservation = false

		if (frame < path.plan.frames.size) {
			val predicted = path.plan.frames[frame].state.position
			walk.telemetry.recordTrail(player.pos, player.pos.distanceTo(predicted))
		}

		if (result === ExecutionObservationResult.Complete) {
			finishTrajectory(path)
			return false
		}
		return true
	}

	private fun SafeContext.finishTrajectory(path: PublishedPath) {
		if (path.partial) {
			enterHold(path)
			return
		}
		walk.cursor = null
		walk.planningSession?.updateExecutionFrame(null)
		walk.tickInput = null
		walk.awaitingObservation = false
		walk.cancelPlanning()
		walk.cancelSuccessor()

		walk.state = State.Complete(path.plan.tape.frameCount, walk.leg)
		walk.finish()
		val telemetry = walk.telemetry
		info(
			"Reached ${path.finalGoal}: ${path.plan.tape.frameCount} frames, " +
					"${path.publicationSequence.coerceAtLeast(1)} publication(s), " +
					"${telemetry.adopted} adoption(s), ${walk.holds} hold(s)" +
					(if (telemetry.recoveries > 0) ", recovered ${telemetry.recoveries} time(s)" else "") +
					(if (walk.repairs > 0) ", repaired ${walk.repairs} time(s)" else "") +
					(if (telemetry.rejectedImprovements > 0) ", rejected ${telemetry.rejectedImprovements}" else "") +
					"; max replay deviation %.2e".format(telemetry.maxDeviation),
			PATHING_SOURCE,
		)
		LOG.info(
			"Pathing tape profile: {} frames vs {} bound = {} optimal; {} standing still " +
					"({}%) in {} stop(s); {}",
			path.plan.tape.frameCount, "%.0f".format(path.route.lowerBoundTicks),
			"%.2fx".format(path.excessRatio), path.standingFrames(), path.standingPercent(),
			path.standingRuns(), walk.publicationCadence(),
		)
		LOG.info("Pathing frames by movement: {}", path.movementProfile())
		LOG.info("Pathing {}", path.approachProfile())
		walk.continueRoute()
	}

	private fun SafeContext.enterHold(path: PublishedPath) {
		val cursor = walk.cursor ?: return
		if (!walk.holding) {
			walk.holding = true
			walk.holds++
			info(
				"Holding at the tape terminal (${path.plan.tape.frameCount} frames) " +
						"while the search continues toward ${path.finalGoal}.",
				PATHING_SOURCE,
			)
		}
		walk.tickInput = ALIGNMENT_INPUT
		walk.awaitingObservation = false
		walk.planningSession?.updateExecutionFrame(cursor.nextFrame)
		walk.request.runSafeAutomated {
			rotationRequest { yaw(path.plan.frames.last().state.rotation.yaw) }.submit()
		}
		walk.state = State.Executing(cursor.nextFrame, path.plan.tape.frameCount, walk.leg)

		if (walk.planningSession == null) {
			walk.holding = false
			val reason = walk.sessionFailure
			walk.sessionFailure = null
			if (reason != null && walk.sessionRestarts >= MAX_SESSION_RESTARTS) {
				return with(walk) { fail("planning kept dead-ending: $reason") }
			}
			if (reason != null) walk.sessionRestarts++
			walk.cursor = null
			walk.tickInput = null

			val successor = walk.successorPath
			walk.successorPath = null
			walk.successorSession?.cancel()
			walk.successorSession = null
			if (successor != null) {
				LOG.info("Installing the successor tape planned during replay")
				return with(admission) { begin(successor) }
			}
			with(walk) { planTrajectory() }
		}
	}

	private fun repairUntil(path: PublishedPath): Int = walk.repairDeadline ?: path.plan.tape.frameCount

	private fun SafeContext.announcePassedWaypoints(path: PublishedPath, frame: Int) {

		val waypoints = walk.request.waypoints
		for ((touchFrame, waypoint) in path.legTouches) {
			if (touchFrame > frame) break
			val next = waypoints.getOrNull(walk.passedWaypoints) ?: break
			if (waypoint != next && waypoint != TrajectoryPlanner.resolveGoalStance(player, next)) continue
			walk.passedWaypoints++
			walk.leg++
			val remaining = walk.remainingWaypoints().size
			info(
				"Passed $waypoint at frame $touchFrame" +
						(if (remaining > 0) ", $remaining waypoint(s) before ${path.finalGoal}." else ", heading to ${path.finalGoal}."),
				PATHING_SOURCE,
			)
		}
	}

	private fun SafeContext.handleDeviation(
		path: PublishedPath,
		frame: Int,
		deviation: ExecutionDeviation,
		observed: MovementSimulationState,
		afterInput: Boolean,
	) {
		if (deviation is ExecutionDeviation.WorldChanged && walk.planningSession != null) {
			val first = path.plan.firstFrameReading(deviation.mutation)
			val graph = first?.let { PlanGraph.of(path.plan) }
			val junction = graph?.repairJunction(first, frame, REPAIR_MIN_LEAD_FRAMES)
			if (junction != null && (walk.repairDeadline?.let { junction.frame < it } != false)) {
				walk.repairDeadline = junction.frame
				walk.repairDeviation = deviation
				LOG.info(
					"World changed under frame {} of the running tape; replaying to junction frame {} while the search repairs the tail",
					first, junction.frame,
				)
				return
			}
		}
		reject(frame, deviation, observed, afterInput)
	}

	fun SafeContext.reject(
		frame: Int,
		deviation: ExecutionDeviation,
		observed: MovementSimulationState,
		afterInput: Boolean,
	) {
		val report = executionRejectionReport(walk.telemetry.published, frame, deviation, observed, afterInput)
		with(walk) {
			when (deviation) {
				is ExecutionDeviation.WorldChanged -> recover(report, reuseCoarseState = true)
				is ExecutionDeviation.PhysicsProfile -> recover(report, reuseCoarseState = false)

				else ->
					if (telemetry.recoveries < MAX_STATE_RECOVERIES) recover(report, reuseCoarseState = true)
					else fail(report)
			}
		}
	}

	private companion object {

		const val REPAIR_MIN_LEAD_FRAMES = 8
	}

	fun SafeContext.observe(plan: TrajectoryPlan, frame: Int) =
		MovementSimulationState.from(player, isJumping = frame > 0 && frame <= plan.tape.frameCount && plan.tape[frame - 1].jump)
}
