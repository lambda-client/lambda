package com.lambda.pathing.trajectory

import com.lambda.pathing.movement.TerminalApproach
import com.lambda.util.player.prediction.MovementSimulationInput

/** Frames a finished tape spent on one movement kind. */
data class TapeSegment(val movement: com.lambda.pathing.core.MovementId, val frames: Int)

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

    /**
     * How the tape's frames divide between the movements that produced them.
     *
     * The terminal run is attributed to whatever the last anchor was doing, which is
     * close enough: it is a brake or a corridor follow either way.
     */
    fun segments(): List<TapeSegment> {
        val out = ArrayList<TapeSegment>()
        var node: ValueAnchor? = anchor
        while (node != null && node.parent != null) {
            out += TapeSegment(node.via ?: com.lambda.pathing.core.MovementId.WALK, node.inputs.size)
            node = node.parent
        }
        out.reverse()
        val tail = frames - out.sumOf { it.frames }
        if (tail > 0) out += TapeSegment(com.lambda.pathing.core.MovementId.WALK, tail)
        return out
    }

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
