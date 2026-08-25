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
}
