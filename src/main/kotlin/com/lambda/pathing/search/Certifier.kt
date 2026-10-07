package com.lambda.pathing.search

import com.lambda.pathing.rollout.SimulatedTrajectoryFrame
import com.lambda.pathing.rollout.TrajectoryRolloutEngine

import com.lambda.pathing.actions.InputTape
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment

internal class Certifier(
	private val initialState: MovementSimulationState,
	private val profile: PlayerPhysicsProfile,
	private val environment: SnapshotSimulationEnvironment,
	private val cancelled: () -> Boolean,
	private val route: RouteState,
	private val attempts: AttemptAccumulator,
) {
	private var certifiedFrames: List<SimulatedTrajectoryFrame> = emptyList()
	private var certifiedDependencies: List<Set<VoxelPos>> = emptyList()

	fun forget() {
		certifiedFrames = emptyList()
		certifiedDependencies = emptyList()
	}

	fun certify(solution: Solution): MotionPlanResult {
		if (cancelled()) return MotionPlanResult.Cancelled
		val inputs = solution.inputs
		val tape = InputTape(inputs)

		var shared = 0
		val maxShared = minOf(inputs.size, certifiedFrames.size)
		while (shared < maxShared && inputs[shared] == certifiedFrames[shared].input) shared++

		val frames = ArrayList<SimulatedTrajectoryFrame>(inputs.size)
		frames += certifiedFrames.subList(0, shared)
		val frameDependencies = ArrayList<Set<VoxelPos>>(inputs.size)
		frameDependencies += certifiedDependencies.subList(0, shared)

		val resumeState = if (shared == 0) initialState else certifiedFrames[shared - 1].state
		val suffix = inputs.subList(shared, inputs.size)
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
			return MotionPlanResult.UnstableReplay(
				"value-field tape did not reproduce: ${certified.termination}"
			)
		}
		certified.frames.forEach { frame -> frames += frame.copy(index = shared + frame.index) }
		check(frameDependencies.size == inputs.size) {
			"Every certified input must publish its world-read dependencies"
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
			legTouches = route.touches.filter { it.frame <= solution.frames },
		)
	}
}
