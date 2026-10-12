package com.lambda.pathing.session

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.pathing.execution.ExecutionDeviation
import com.lambda.pathing.execution.ImprovementArbiter
import com.lambda.pathing.execution.TrajectoryExecutionCursor
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.search.replaysUnchanged
import com.lambda.pathing.session.AlignmentController.Companion.FIRST_FRAME_YAW_EPSILON
import com.lambda.pathing.session.AlignmentController.Companion.START_DRIFT_TOLERANCE
import com.lambda.pathing.session.PathingSession.Companion.ALIGNMENT_INPUT
import com.lambda.pathing.session.PathingSession.Companion.PATHING_SOURCE
import com.lambda.pathing.session.PathingSession.State
import com.lambda.util.CommunicationUtils.info

internal class TapeAdmission(private val walk: PathingSession) {

	fun SafeContext.begin(path: PublishedPath) {
		walk.afloatStart?.let { afloat ->
			walk.afloatStart = null
			if (afloat.state == path.plan.initialState) return joinAfloat(path, player.age - afloat.age)
		}
		executionEnvironmentDeviation(path, nextFrame = 0)?.let { return staleBeforeExecution(it) }

		val drift = with(walk.alignment) { startDrift(path) }
		if (drift > START_DRIFT_TOLERANCE) {
			if (walk.launcher.current == null) return with(walk) { planTrajectory() }
			return with(walk) { recoverOrFail("moved %.2f blocks while planning".format(drift)) }
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

	private fun SafeContext.joinAfloat(path: PublishedPath, frame: Int) {
		if (frame >= path.plan.tape.frameCount) return with(walk) { recoverOrFail("treaded $frame frames before the first tape arrived") }
		executionEnvironmentDeviation(path, nextFrame = frame)?.let { return staleBeforeExecution(it) }
		walk.alignment.installed()
		replay(path, frame)
		walk.leg++
		walk.state = State.Executing(frame, path.plan.tape.frameCount, walk.leg)
		info("Certified trajectory afloat; joined it at frame $frame after treading water while planning.", PATHING_SOURCE)
	}

	private fun SafeContext.staleBeforeExecution(deviation: ExecutionDeviation) = with(walk) {
		recover("certified plan became stale before execution: $deviation", reuseCoarseState = deviation !is ExecutionDeviation.PhysicsProfile)
	}

	private fun keepRunning(rejected: PublishedPath, reason: String) {
		walk.launcher.current?.refuse(rejected.publicationSequence.toLong())
		walk.telemetry.countRejectedImprovement()
		walk.replay?.takeIf { it.holding }?.let { held ->
			held.heldRefusals++
			held.lastHeldRefusal = reason
		}

		LOG.debug("Pathing kept the running tape: $reason (${rejected.plan.tape.frameCount} frames offered)")
	}

	fun SafeContext.install(path: PublishedPath) {
		executionEnvironmentDeviation(path, nextFrame = 0)?.let { return staleBeforeExecution(it) }
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
		val replay = walk.replay?.takeIf { it.path === path }
		val since = maxOf(path.plan.snapshotRevision, replay?.verifiedRevision ?: Long.MIN_VALUE)
		val revision = world.revision
		if (revision <= since) return null
		val mutation = world.mutationsSince(since)
			.firstOrNull { path.plan.reads(it, nextFrame, untilFrame) } ?: return null
		if (replay != null) {
			world.applyPending()
			if (path.plan.replaysUnchanged(world.snapshot, nextFrame, untilFrame)) {
				replay.verifiedRevision = revision
				walk.telemetry.countVerifiedChange()
				return null
			}
		}
		return ExecutionDeviation.WorldChanged(path.plan.snapshotRevision, mutation)
	}

}
