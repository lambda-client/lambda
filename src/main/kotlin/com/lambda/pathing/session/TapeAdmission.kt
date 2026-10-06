package com.lambda.pathing.session

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.pathing.execution.ExecutionDeviation
import com.lambda.pathing.execution.ImprovementArbiter
import com.lambda.pathing.execution.TrajectoryExecutionCursor
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.session.AlignmentController.Companion.FIRST_FRAME_YAW_EPSILON
import com.lambda.pathing.session.AlignmentController.Companion.START_DRIFT_TOLERANCE
import com.lambda.pathing.session.PathingSession.Companion.ALIGNMENT_INPUT
import com.lambda.pathing.session.PathingSession.Companion.PATHING_SOURCE
import com.lambda.pathing.session.PathingSession.State
import com.lambda.util.CommunicationUtils.info

internal class TapeAdmission(private val walk: PathingSession) {

	fun SafeContext.begin(path: PublishedPath) {
		executionEnvironmentDeviation(path, nextFrame = 0)?.let { deviation ->
			return with(walk) { fail("certified plan became stale before execution: $deviation") }
		}

		val drift = with(walk.alignment) { startDrift(path) }
		if (drift > START_DRIFT_TOLERANCE) {
			if (walk.launcher.current == null) {
				return keepRunning(path, "publication was certified from a stance the walk has left")
			}
			return with(walk) { fail("moved %.2f blocks while planning".format(drift)) }
		}

		val yawDrift = with(walk.alignment) { launchYawError(path) }
		if (yawDrift > FIRST_FRAME_YAW_EPSILON) {
			walk.telemetry.published = path
			walk.alignment.beginAligning(path)
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
		val replay = walk.replay
		when (val verdict = ImprovementArbiter.judge(
			running = replay?.path,
			cursorFrame = replay?.cursor?.nextFrame,
			awaitingObservation = replay?.cursor?.awaitingObservation == true,
			offered = path,
			runningInvalid = replay?.repairDeadline != null,
		)) {
			ImprovementArbiter.Verdict.BeginFresh -> begin(path)

			is ImprovementArbiter.Verdict.Keep -> keepRunning(path, verdict.reason)

			ImprovementArbiter.Verdict.DeferForObservation -> replay?.pendingImprovement = path

			is ImprovementArbiter.Verdict.Adopt -> {
				val frame = verdict.frame
				executionEnvironmentDeviation(path, nextFrame = frame)?.let { deviation ->
					return keepRunning(path, "publication environment changed: $deviation")
				}
				val repairCut = replay?.repairDeadline
				replay(path, frame)
				walk.launcher.progressed()
				if (repairCut != null) {
					walk.telemetry.countRepair()
					LOG.info("Adopted the repaired tape at frame {} (cut was frame {})", frame, repairCut)
				}
				walk.state = State.Executing(frame, path.plan.tape.frameCount, walk.leg)
				walk.telemetry.countAdoption(path.plan.tape.frameCount)
			}
		}
	}

	private fun keepRunning(rejected: PublishedPath, reason: String) {
		walk.telemetry.countRejectedImprovement()

		LOG.debug("Pathing kept the running tape: $reason (${rejected.plan.tape.frameCount} frames offered)")
	}

	fun SafeContext.install(path: PublishedPath) {
		executionEnvironmentDeviation(path, nextFrame = 0)?.let { deviation ->
			return with(walk) { fail("certified plan became stale before execution: $deviation") }
		}
		walk.face(path.plan.initialState.rotation.yaw)
		walk.alignment.installed()
		replay(path, frame = 0)
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

	private fun replay(path: PublishedPath, frame: Int) {
		walk.telemetry.published = path
		walk.journey?.world?.let { InterestPrimer.primeRoute(it, path.route) }
		walk.replay = Replay(path, TrajectoryExecutionCursor(path.plan, frame))
		walk.launcher.current?.adopt(frame, path.publicationSequence.toLong())
	}

	fun SafeContext.executionEnvironmentDeviation(
		path: PublishedPath,
		nextFrame: Int,
		untilFrame: Int = path.plan.tape.frameCount,
	): ExecutionDeviation? {
		val liveProfile = with(walk) { liveProfile() }
		if (!liveProfile.isCompatibleWith(path.plan.physicsProfile)) {
			return ExecutionDeviation.PhysicsProfile(path.plan.physicsProfile, liveProfile)
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
