package com.lambda.pathing.search

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.actions.InputTape
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment

/**
 * Replays a solution's tape from the session's initial state and packages the certified
 * frames. The segments it publishes are `solution.planSegments`, which are the plan graph's
 * best route by construction (see [Solution.planSegments]).
 */
internal class Certifier(
    private val initialState: MovementSimulationState,
    private val profile: PlayerPhysicsProfile,
    private val environment: SnapshotSimulationEnvironment,
    private val cancelled: () -> Boolean,
) {
    private var certifiedInputs: List<MovementSimulationInput> = emptyList()
    private var certifiedFrames: List<SimulatedTrajectoryFrame> = emptyList()
    private var certifiedDependencies: List<Set<VoxelPos>> = emptyList()

    fun certify(
        solution: Solution,
        route: CoarseRoutePlan,
        remainingGuideTicks: Double,
        attemptCount: Int,
    ): MotionPlanResult {
        if (cancelled()) return MotionPlanResult.Cancelled
        val inputs = solution.inputs
        val tape = InputTape(inputs)

        var shared = 0
        val maxShared = minOf(inputs.size, certifiedInputs.size, certifiedFrames.size)
        while (shared < maxShared && inputs[shared] == certifiedInputs[shared]) shared++

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
        val dependencies = HashSet<VoxelPos>()
        frameDependencies.forEach(dependencies::addAll)

        certifiedInputs = inputs
        certifiedFrames = frames
        certifiedDependencies = frameDependencies

        if (cancelled()) return MotionPlanResult.Cancelled
        return MotionPlanResult.Success(
            sourceRoute = route,
            tape = tape,
            rollout = TrajectoryRollout(initialState, frames, TrajectoryRolloutTermination.Completed),
            parameters = solution.parameters,
            safeAnchorStance = solution.anchor.stance,
            safeAnchorFrame = solution.anchor.elapsed,
            remainingGuideTicks = remainingGuideTicks,
            frameDependencies = frameDependencies,

            dependencies = dependencies,
            attemptCount = attemptCount,
            controlSegments = solution.segments,
            spliceFrames = solution.boundaries.filter { it in 1 until tape.frameCount },
            launchMarginFrames = solution.launchMargin,
            segments = solution.segments(),
            planSegments = solution.planSegments,
        )
    }
}
