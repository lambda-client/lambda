package com.lambda.pathing.session

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.execution.ExecutionDeviation
import com.lambda.pathing.execution.ExecutionStateTolerance
import com.lambda.pathing.execution.ImprovementArbiter
import com.lambda.pathing.execution.TrajectoryExecutionCursor
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.session.AlignmentController.Companion.FIRST_FRAME_YAW_EPSILON
import com.lambda.pathing.session.AlignmentController.Companion.START_DRIFT_TOLERANCE
import com.lambda.pathing.session.PathingSession.Companion.ALIGNMENT_INPUT
import com.lambda.pathing.session.PathingSession.Companion.PATHING_SOURCE
import com.lambda.pathing.session.PathingSession.State
import com.lambda.pathing.world.InterestPrimer
import com.lambda.threading.runSafeAutomated
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.player.MovementUtils.moveYaw
import kotlin.math.abs

/**
 * How a publication reaches the body: [begin] installs the first tape of a leg (after
 * alignment when the launch yaw is off), [adopt] swaps a running tape for an improvement
 * under the arbiter's verdict, and [install] hands a tape to a fresh cursor. Every path
 * first checks the certified environment still holds.
 */
internal class TapeAdmission(private val walk: PathingSession) {

	fun SafeContext.begin(path: PublishedPath) {
		walk.planningSession?.let { session ->
			if (path.planningGeneration != session.generation) {
				return keepRunning(path, "publication belongs to stale planning generation ${path.planningGeneration}")
			}
		}
		executionEnvironmentDeviation(path, nextFrame = 0)?.let { deviation ->
			return with(walk) { fail("certified plan became stale before execution: $deviation") }
		}
		if (!path.partial && path.route.goal != path.finalGoal) {
			return with(walk) { fail("planner attempted to publish a partial route ending at ${path.route.goal}") }
		}

		val drift = player.pos.distanceTo(path.plan.initialState.position)
		if (drift > START_DRIFT_TOLERANCE) {
			if (walk.planningSession?.generation != path.planningGeneration) {
				return keepRunning(path, "publication was certified from a stance the walk has left")
			}
			return with(walk) { fail("moved %.2f blocks while planning".format(drift)) }
		}

		val yawDrift = abs(Rotation.wrap(player.moveYaw - path.plan.initialState.rotation.yaw))
		if (yawDrift > FIRST_FRAME_YAW_EPSILON) {
			walk.telemetry.published = path
			walk.pendingPath = path
			walk.alignmentTicks = 0
			walk.tickInput = ALIGNMENT_INPUT
			walk.state = State.Aligning(walk.leg + 1, yawDrift)
			info(
				"Certified trajectory; aligning movement yaw by %.2f° before replay.".format(yawDrift),
				PATHING_SOURCE,
			)
			return
		}

		install(path)
	}

	fun SafeContext.adopt(path: PublishedPath) {
		when (val verdict = ImprovementArbiter.judge(
			running = walk.telemetry.published,
			cursorFrame = walk.cursor?.nextFrame,
			awaitingObservation = walk.awaitingObservation,
			offered = path,
			runningInvalidFrom = walk.repairDeadline,
		)) {
			ImprovementArbiter.Verdict.BeginFresh -> begin(path)

			is ImprovementArbiter.Verdict.Keep -> keepRunning(path, verdict.reason)

			ImprovementArbiter.Verdict.DeferForObservation -> walk.pendingImprovement = path

			is ImprovementArbiter.Verdict.Adopt -> {
				val frame = verdict.frame
				executionEnvironmentDeviation(path, nextFrame = frame)?.let { deviation ->
					return keepRunning(path, "publication environment changed: $deviation")
				}
				walk.telemetry.published = path
				walk.telemetry.recordExecuted(path)
				walk.journey?.world?.let { InterestPrimer.primeRoute(it, path.route) }
				walk.cursor = TrajectoryExecutionCursor(
					path.plan, with(walk) { liveProfile() },
					tolerance = ExecutionStateTolerance(),
				).apply { resumeAt(frame) }
				walk.planningSession?.updateExecutionFrame(frame)
				walk.planningSession?.adoptedSequence = path.publicationSequence.toLong()
				walk.recordAdoption(path.plan.tape.frameCount)
				walk.holding = false
				walk.sessionRestarts = 0
				if (walk.repairDeadline != null) {
					walk.repairs++
					LOG.info("Adopted the repaired tape at frame {} (cut was frame {})", frame, walk.repairDeadline)
				}
				walk.repairDeadline = null
				walk.repairDeviation = null
				walk.state = State.Executing(frame, path.plan.tape.frameCount, walk.leg)
				walk.telemetry.countAdoption()
			}
		}
	}

	private fun keepRunning(rejected: PublishedPath, reason: String) {
		walk.telemetry.countRejectedImprovement()
		// Debug, deliberately: a healthy anytime walk rejects a partial publication
		// about once a second, and at info that drowned every other pathing line.
		LOG.debug("Pathing kept the running tape: $reason (${rejected.plan.tape.frameCount} frames offered)")
	}

	fun SafeContext.install(path: PublishedPath) {
		executionEnvironmentDeviation(path, nextFrame = 0)?.let { deviation ->
			return with(walk) { fail("certified plan became stale before execution: $deviation") }
		}
		walk.request.runSafeAutomated {
			rotationRequest { yaw(path.plan.initialState.rotation.yaw) }.submit()
		}
		walk.journey?.world?.let { InterestPrimer.primeRoute(it, path.route) }
		walk.planningYaw = null
		walk.pendingPath = null
		walk.alignmentTicks = 0
		walk.telemetry.published = path
		walk.telemetry.recordExecuted(path)
		walk.cursor = TrajectoryExecutionCursor(
			path.plan, with(walk) { liveProfile() },
			tolerance = ExecutionStateTolerance(),
		)
		walk.planningSession?.updateExecutionFrame(0)
		walk.planningSession?.adoptedSequence = path.publicationSequence.toLong()
		walk.awaitingObservation = false
		walk.holding = false
		walk.repairDeadline = null
		walk.repairDeviation = null
		walk.leg++
		walk.state = State.Executing(0, path.plan.tape.frameCount, walk.leg)
		info(
			"Certified continuous trajectory: ${path.route.nodes.first()} -> ${path.route.goal}, " +
					"${path.plan.tape.frameCount} frames, ${path.attempts} attempts, " +
					"${path.planMillis} ms, ${path.controlSegments} continuous segment(s)" +
					path.spliceFrames.takeIf { it.isNotEmpty() }
						?.let { ", predicted splice frames ${it.joinToString()}" }.orEmpty(),
			PATHING_SOURCE,
		)
	}

	fun SafeContext.executionEnvironmentDeviation(
		path: PublishedPath,
		nextFrame: Int,
		/** Frames at or past this are not going to be replayed (a repair cut), so their reads do not count. */
		untilFrame: Int = path.plan.tape.frameCount,
	): ExecutionDeviation? {
		val liveProfile = with(walk) { liveProfile() }
		if (!liveProfile.isCompatibleWith(path.profile)) {
			return ExecutionDeviation.PhysicsProfile(path.profile, liveProfile)
		}
		val world = walk.journey?.world ?: return null
		if (world.revision <= path.plan.snapshotRevision) return null
		val mutation = world.changedSince(
			snapshotRevision = path.plan.snapshotRevision,
			dependedSections = path.plan.dependencySectionsBetween(nextFrame, untilFrame),
			dependedChunks = path.plan.dependencyChunksBetween(nextFrame, untilFrame),
		) ?: return null
		return ExecutionDeviation.WorldChanged(path.plan.snapshotRevision, mutation)
	}
}
