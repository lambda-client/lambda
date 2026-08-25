package com.lambda.pathing.trajectory

import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.movement.ControlProgram
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment

internal class GatedRollout(
    val rollout: TrajectoryRollout,
    val stopFrame: Int?,
    val failed: Boolean,
)

internal class RolloutGate(
    private val profile: PlayerPhysicsProfile,
    private val environment: SnapshotSimulationEnvironment,
    private val config: MotionConstraints,
    private val goalPoint: () -> HorizontalPoint,
) {
    fun run(
        from: MovementSimulationState,
        points: List<HorizontalPoint>,
        program: ControlProgram,
        frameCount: Int,
    ): GatedRollout {
        val evaluator = RolloutEvaluator(from, points, goalPoint(), config)
        var previous = from
        var stopFrame: Int? = null
        var failed = false
        val rollout = TrajectoryRolloutEngine.rollout(
            initialState = from,
            profile = profile,
            environment = environment,
            program = program,
            frameCount = frameCount,
        ) { frame ->
            val verdict = evaluator.observe(frame.index, frame.state, previous)
            previous = frame.state
            when (verdict) {
                is RolloutVerdict.Stopped -> { stopFrame = verdict.frame; true }
                is RolloutVerdict.Failed -> { failed = true; true }
                is RolloutVerdict.Continue -> false
            }
        }
        return GatedRollout(rollout, stopFrame, failed)
    }
}
