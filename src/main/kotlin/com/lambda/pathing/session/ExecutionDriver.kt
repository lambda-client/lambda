package com.lambda.pathing.session

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.pathing.debug.PlanDump
import com.lambda.pathing.debug.ServerCorrections
import com.lambda.pathing.debug.executionRejectionReport
import com.lambda.pathing.execution.ExecutionDeviation
import com.lambda.pathing.execution.ExecutionInputResult
import com.lambda.pathing.execution.ExecutionObservationResult
import com.lambda.pathing.execution.TrajectoryExecutionCursor
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.search.TrajectoryPlan
import com.lambda.pathing.search.repairFrame
import com.lambda.pathing.session.PathingSession.Companion.ALIGNMENT_INPUT
import com.lambda.pathing.session.PathingSession.Companion.PATHING_SOURCE
import com.lambda.pathing.session.PathingSession.Companion.TREAD_WATER_INPUT
import com.lambda.pathing.session.PathingSession.State
import com.lambda.pathing.world.snapshot.HazardRules
import com.lambda.util.CommunicationUtils.info
import net.minecraft.util.math.MathHelper
import kotlin.math.abs

internal class ExecutionDriver(private val walk: PathingSession) {
	fun SafeContext.tickExecution() {
		val active = walk.replay ?: return

		walk.launcher.current?.updateExecutionFrame(active.cursor.nextFrame)
		if (active.cursor.awaitingObservation && !finishObservedTick(active)) return continueReplaced(active)

		active.pendingImprovement?.let { improvement ->
			active.pendingImprovement = null
			with(walk.admission) { adopt(improvement) }
		}

		val replay = walk.replay ?: return
		if (replay.bridged && with(walk.launcher) { joinBridge(replay) }) return continueReplaced(replay)
		val current = replay.path
		val running = replay.cursor
		if (!current.partial && running.nextFrame >= current.plan.stationaryFrom && running.nextFrame < current.plan.tape.frameCount) {
			return finishTrajectory(replay)
		}
		if (replay.holding) return enterHold(replay)

		if (running.nextFrame == 0 && current.plan.initialState.velocity.horizontalLengthSquared() == 0.0 && with(walk.alignment) { realignBeforeLaunch(current) }) return

		val observed = observe(current.plan, running.nextFrame)
		announcePassedWaypoints(current, running.nextFrame)
		replay.repairDeadline?.let { deadline ->
			if (running.nextFrame >= deadline) {
				val deviation = replay.repairDeviation ?: ExecutionDeviation.Protocol("repair deadline reached")
				return reject(current, running.nextFrame, deviation, observed, afterInput = false)
			}
		}
		with(walk.admission) {
			executionEnvironmentDeviation(current, nextFrame = running.nextFrame, untilFrame = repairUntil(replay))
		}?.let { deviation ->
			return handleDeviation(replay, running.nextFrame, deviation, observed, afterInput = false)
		}
		when (val next = running.nextInput(observed)) {
			is ExecutionInputResult.Apply -> {
				walk.tickInput = next.input
				walk.state = State.Executing(next.frame, current.plan.tape.frameCount, walk.leg)
				next.input.rotation?.let { rotation ->
					val wet = observed.touchingWater || current.plan.frames.getOrNull(next.frame)?.state?.touchingWater == true
					walk.face(rotation.yaw, rotation.pitch.takeIf { wet })
				}
			}

			ExecutionInputResult.Complete -> finishTrajectory(replay)

			is ExecutionInputResult.Rejected -> {
				reject(current, next.frame, next.deviation, observed, afterInput = false)
				continueReplaced(replay)
			}
		}
	}

	private fun SafeContext.continueReplaced(previous: Replay) {
		val next = walk.replay ?: return
		if (next !== previous && next.cursor.nextFrame == 0 && !next.cursor.awaitingObservation) tickExecution()
	}

	private fun SafeContext.finishObservedTick(replay: Replay): Boolean {
		val path = replay.path
		val frame = replay.cursor.nextFrame
		val observed = observe(path.plan, frame + 1)
		replay.recordObserved(frame, observed)
		with(walk.admission) {
			executionEnvironmentDeviation(path, nextFrame = frame, untilFrame = repairUntil(replay))
		}?.let { deviation ->
			handleDeviation(replay, frame, deviation, observed, afterInput = true)
			return false
		}
		val result = replay.cursor.observeAfterTick(observed)
		if (result is ExecutionObservationResult.Rejected) {
			reject(path, result.frame, result.deviation, observed, afterInput = true)
			return false
		}

		if (frame < path.plan.frames.size) {
			val predicted = path.plan.frames[frame].state.position
			walk.telemetry.recordTrail(player.pos, player.pos.distanceTo(predicted))
		}

		if (result === ExecutionObservationResult.Complete) {
			finishTrajectory(replay)
			return false
		}
		replay.teleportsSeen = ServerCorrections.teleports
		return true
	}

	private fun SafeContext.finishTrajectory(replay: Replay) {
		if (replay.path.partial) return enterHold(replay)
		complete(replay)
	}

	private fun SafeContext.complete(replay: Replay) {
		val path = replay.path
		val frame = replay.cursor.nextFrame
		walk.dumpEvent(
			"arrived", "reached ${path.finalGoal} after ${path.plan.tape.frameCount} frames",
			PlanDump.ExecutionTrace.of(path.plan, frame, afterInput = false, observe(path.plan, frame), replay.observedHistory.toList()),
		)
		walk.replay = null
		walk.tickInput = null
		walk.state = State.Complete(path.plan.tape.frameCount, walk.leg)
		walk.release(park = true)
		val telemetry = walk.telemetry
		info(
			"Reached ${path.finalGoal}: ${path.plan.tape.frameCount} frames, " +
					"${path.publicationSequence.coerceAtLeast(1)} publication(s), " +
					"${telemetry.adopted} adoption(s), ${telemetry.holds} hold(s)" +
					(if (telemetry.recoveries > 0) ", recovered ${telemetry.recoveries} time(s)" else "") +
					(if (telemetry.repairs > 0) ", repaired ${telemetry.repairs} time(s)" else "") +
					(if (telemetry.rejectedImprovements > 0) ", rejected ${telemetry.rejectedImprovements}" else "") +
					"; max replay deviation %.2e".format(telemetry.maxDeviation),
			PATHING_SOURCE,
		)
		LOG.info(
			"Pathing tape profile: {} frames vs {} bound = {} optimal; {} standing still " +
					"({}%) in {} stop(s); {}",
			path.plan.tape.frameCount, "%.0f".format(path.route.lowerBoundTicks),
			"%.2fx".format(path.excessRatio), path.standingFrames(), path.standingPercent(),
			path.standingRuns(), telemetry.cadence(),
		)
		LOG.info("Pathing frames by movement: {}", path.movementProfile())
		LOG.info("Pathing {}", path.approachProfile())
		walk.continueRoute()
	}

	private fun SafeContext.enterHold(replay: Replay) {
		val path = replay.path
		if (!replay.holding) {
			replay.holding = true
			replay.heldSince = System.currentTimeMillis()
			walk.heartbeat.soon()
			walk.telemetry.countHold()
			walk.dumpEvent("hold", "holding at the tape terminal after ${path.plan.tape.frameCount} frames toward ${path.finalGoal}")
			info(
				"Holding at the tape terminal (${path.plan.tape.frameCount} frames) " +
						"while the search continues toward ${path.finalGoal}.",
				PATHING_SOURCE,
			)
		}
		walk.tickInput = if (player.isTouchingWater) TREAD_WATER_INPUT else ALIGNMENT_INPUT
		walk.launcher.current?.updateExecutionFrame(replay.cursor.nextFrame)
		walk.face(path.plan.frames.last().state.rotation.yaw)
		walk.state = State.Executing(replay.cursor.nextFrame, path.plan.tape.frameCount, walk.leg)

		if (walk.launcher.current == null) {
			replay.holding = false
			with(walk.launcher) { resumeAfterHold() }
		}
	}

	private fun repairUntil(replay: Replay): Int = replay.repairDeadline ?: replay.path.plan.tape.frameCount

	private fun SafeContext.announcePassedWaypoints(path: PublishedPath, frame: Int) {
		val waypoints = walk.request.waypoints
		for ((touchFrame, waypoint) in path.legTouches) {
			if (touchFrame > frame) break
			val next = waypoints.getOrNull(walk.passedWaypoints) ?: break
			if (waypoint != next && waypoint != resolveGoalStance(player, next)) continue
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
		replay: Replay,
		frame: Int,
		deviation: ExecutionDeviation,
		observed: MovementSimulationState,
		afterInput: Boolean,
	) {
		val path = replay.path
		if (deviation is ExecutionDeviation.WorldChanged && walk.launcher.current != null) {
			val first = path.plan.firstFrameReading(deviation.mutation)
			val junction = first?.let { path.plan.segments.repairFrame(it, frame, REPAIR_MIN_LEAD_FRAMES) }
			if (junction != null && (replay.repairDeadline?.let { junction < it } != false)) {
				replay.repairDeadline = junction
				replay.repairDeviation = deviation
				LOG.info(
					"World changed under frame {} of the running tape; replaying to junction frame {} while the search repairs the tail",
					first, junction,
				)
				return
			}
		}
		reject(path, frame, deviation, observed, afterInput)
	}

	fun SafeContext.reject(
		path: PublishedPath,
		frame: Int,
		deviation: ExecutionDeviation,
		observed: MovementSimulationState,
		afterInput: Boolean,
	) {
		if (rebase(path, frame, deviation, observed, afterInput)) return
		val journey = walk.journey
		val report = executionRejectionReport(path, frame, deviation, observed, afterInput, journey?.world?.snapshot, journey?.hazards ?: HazardRules.AVOID_ALL)
		val history = walk.replay?.takeIf { it.path === path }?.observedHistory?.toList().orEmpty()
		walk.dumpEvent("divergence", report, PlanDump.ExecutionTrace.of(path.plan, frame, afterInput, observed, history))
		val replay = walk.replay
		if (replay != null && replay.path === path && arrivedBeforeTeleport(replay, frame)) {
			info("The server moved the body away on arrival at ${path.finalGoal}.", PATHING_SOURCE)
			return complete(replay)
		}
		with(walk) {
			when (deviation) {
				is ExecutionDeviation.WorldChanged -> recover(report, reuseCoarseState = true)
				is ExecutionDeviation.PhysicsProfile -> recover(report, reuseCoarseState = false)

				else -> recoverOrFail(report)
			}
		}
	}

	private fun SafeContext.rebase(
		path: PublishedPath,
		frame: Int,
		deviation: ExecutionDeviation,
		observed: MovementSimulationState,
		afterInput: Boolean,
	): Boolean {
		if (deviation !is ExecutionDeviation.Velocity && deviation !is ExecutionDeviation.Position) return false
		val replay = walk.replay?.takeIf { it.path === path && it.repairDeadline == null && !it.holding } ?: return false
		if (replay.bridged && !afterInput && frame == 0) return false
		val world = walk.journey?.world ?: return false
		if (!pushedByEntity()) return false
		val bridge = rebased(path, if (afterInput) frame + 1 else frame, observed, world.terrainSnapshot, world.revision) ?: return false
		walk.launcher.cancel()
		walk.replay = Replay(bridge, TrajectoryExecutionCursor(bridge.plan), bridged = true)
		walk.telemetry.published = bridge
		walk.telemetry.countPush()
		LOG.debug("Pushed off frame {} ({}); replaying the remaining {} frames from the pushed state", frame, deviation, bridge.plan.tape.frameCount)
		return true
	}

	private fun arrivedBeforeTeleport(replay: Replay, frame: Int): Boolean {
		if (walk.remainingWaypoints().isNotEmpty() || ServerCorrections.teleports == replay.teleportsSeen) return false
		val path = replay.path
		val confirmed = if (frame == 0) path.plan.initialState else path.plan.frames.getOrNull(frame - 1)?.state ?: return false
		val goal = path.finalGoal
		return MathHelper.floor(confirmed.position.x) == goal.x && MathHelper.floor(confirmed.position.z) == goal.z &&
				abs(confirmed.position.y - goal.y) <= 1.0
	}

	fun SafeContext.observe(plan: TrajectoryPlan, frame: Int) =
		MovementSimulationState.from(player, isJumping = frame > 0 && frame <= plan.tape.frameCount && plan.tape[frame - 1].jump)

	private companion object {
		const val REPAIR_MIN_LEAD_FRAMES = 8
	}
}
