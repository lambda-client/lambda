package com.lambda.pathing.search

import com.lambda.pathing.rollout.SimulatedTrajectoryFrame
import com.lambda.pathing.rollout.TrajectoryRolloutEngine
import com.lambda.pathing.rollout.TrajectoryRolloutTermination

import com.lambda.pathing.actions.InputTape
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.physics.UnsupportedBlockPhysicsException
import com.lambda.pathing.physics.UnsupportedPhysicsKind
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment

internal class Certifier(
	private val initialState: MovementSimulationState,
	private val profile: PlayerPhysicsProfile,
	private val environment: SnapshotSimulationEnvironment,
	private val cancelled: () -> Boolean,
	private val route: RouteState,
	private val attempts: AttemptAccumulator,
	private val publications: Publications? = null,
) {
	private var certifiedFrames: List<SimulatedTrajectoryFrame> = emptyList()
	private var certifiedDependencies: List<Set<VoxelPos>> = emptyList()

	var certifications = 0
		private set

	var failures = 0
		private set

	var replayedFrames = 0L
		private set

	var lastFailure: String? = null
		private set

	var entityBlocked = false

	fun forget(mutations: Set<VoxelPos>, chunks: Set<PathingChunk>, executed: Int) {
		for (frame in executed.coerceAtLeast(0) until certifiedDependencies.size) {
			if (certifiedDependencies[frame].none { it in mutations || PathingChunk.containing(it) in chunks }) continue
			certifiedFrames = certifiedFrames.subList(0, frame)
			certifiedDependencies = certifiedDependencies.subList(0, frame)
			return
		}
	}

	fun certify(solution: Solution): MotionPlanResult {
		if (cancelled()) return MotionPlanResult.Cancelled
		val inputs = solution.inputs
		val tape = InputTape(inputs)

		var baseFrames = certifiedFrames
		var baseDependencies = certifiedDependencies
		var shared = sharedPrefix(inputs, baseFrames, baseFrames.size)
		val executed = publications?.cursor()
		val running = publications?.running?.plan?.plan
		if (executed != null && running != null && shared < executed) {
			val trusted = sharedPrefix(inputs, running.frames, executed)
			if (trusted > shared) {
				baseFrames = running.frames
				baseDependencies = running.frameDependencies
				shared = trusted
			}
		}

		val frames = ArrayList<SimulatedTrajectoryFrame>(inputs.size)
		frames += baseFrames.subList(0, shared)
		val frameDependencies = ArrayList<Set<VoxelPos>>(inputs.size)
		frameDependencies += baseDependencies.subList(0, shared)

		val resumeState = if (shared == 0) initialState else baseFrames[shared - 1].state
		val suffix = inputs.subList(shared, inputs.size)
		certifications++
		replayedFrames += suffix.size
		val tracked = environment.trackingView()
		val certified = TrajectoryRolloutEngine.rollout(
			initialState = resumeState,
			profile = profile,
			environment = tracked,
			program = InputTape(suffix),
			frameCount = suffix.size,
			observer = { _ ->
				frameDependencies += tracked.takeFrameDependencies()
				false
			},
		)
		if (!certified.completed || certified.frames.size != suffix.size) {
			failures++
			lastFailure = describe(certified.termination, shared)
			val failure = (certified.termination as? TrajectoryRolloutTermination.Rejected)?.failure
			if (failure is UnsupportedBlockPhysicsException && failure.physics.kind == UnsupportedPhysicsKind.ENTITY) entityBlocked = true
			return MotionPlanResult.UnstableReplay(
				"value-field tape did not reproduce: ${certified.termination}"
			)
		}
		certified.frames.forEach { frame -> frames += frame.copy(index = shared + frame.index) }
		check(frameDependencies.size == inputs.size) {
			"Every certified input must publish its world-read dependencies"
		}
		val searched = solution.tailFrames.lastOrNull()?.state ?: solution.anchor.state
		val replayed = frames.lastOrNull()?.state ?: searched
		if (replayed.position != searched.position || replayed.velocity != searched.velocity) {
			failures++
			lastFailure = "replay from f$shared ended at ${replayed.position}, the search at ${searched.position}"
			return MotionPlanResult.UnstableReplay("value-field tape left the searched trajectory: $lastFailure")
		}
		certifiedFrames = frames
		certifiedDependencies = frameDependencies

		if (cancelled()) return MotionPlanResult.Cancelled
		return MotionPlanResult.Success(
			sourceRoute = route.plan,
			plan = TrajectoryPlan(
				snapshotRevision = route.revision,
				physicsProfile = profile,
				initialState = initialState,
				frameDependencies = frameDependencies,
				tape = tape,
				frames = frames,
				segments = solution.planSegments,
			),
			safeAnchorStance = solution.anchor.stance,
			safeAnchorFrame = solution.anchor.elapsed,
			attemptCount = attempts.count,
			legTouches = solution.anchor.legTouches(),
		)
	}

	private fun describe(termination: TrajectoryRolloutTermination, replayedFrom: Int): String = when (termination) {
		is TrajectoryRolloutTermination.Blocked ->
			"unloaded section (${termination.section.x}, ${termination.section.y}, ${termination.section.z}) at f${replayedFrom + termination.frame}, replaying from f$replayedFrom"
		is TrajectoryRolloutTermination.Rejected ->
			"${termination.failure.message} at f${replayedFrom + termination.frame}, replaying from f$replayedFrom"
		TrajectoryRolloutTermination.Completed -> "a short replay from f$replayedFrom"
	}

	private fun sharedPrefix(inputs: List<MovementSimulationInput>, frames: List<SimulatedTrajectoryFrame>, limit: Int): Int {
		val bound = minOf(inputs.size, frames.size, limit)
		var shared = 0
		while (shared < bound && inputs[shared] == frames[shared].input) shared++
		return shared
	}
}

private fun ValueAnchor.legTouches(): List<LegTouch> =
	generateSequence(this) { it.parent }.filter { it.legRoot }.map { LegTouch(it.elapsed, it.stance) }.toList().asReversed()
