package com.lambda.pathing.trajectory

import com.lambda.pathing.movement.TerminalApproach
import com.lambda.util.player.prediction.MovementSimulationInput

internal class Solution(
    val inputs: List<MovementSimulationInput>,
    val boundaries: List<Int>,
    val segments: Int,
    val launchMargin: Int,
    val parameters: TerminalApproach,
    val frames: Int,
    val collisionEvents: Int,
    val anchor: ValueAnchor,
) {
    val score: Int get() = frames + ValueFieldAnchorSearch.COLLISION_FRAME_PENALTY * collisionEvents

    companion object {
        fun of(
            anchor: ValueAnchor,
            tail: List<SimulatedTrajectoryFrame>,
            parameters: TerminalApproach,
            collisionEvents: Int,
        ): Solution = Solution(
            inputs = anchor.prefix() + tail.map { it.input },
            boundaries = anchor.boundaries() + anchor.elapsed,
            segments = anchor.depth() + 1,
            launchMargin = anchor.launchMargin,
            parameters = parameters,
            frames = anchor.elapsed + tail.size,
            collisionEvents = collisionEvents,
            anchor = anchor,
        )
    }
}
