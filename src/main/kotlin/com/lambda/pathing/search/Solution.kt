package com.lambda.pathing.search

import com.lambda.pathing.actions.TerminalApproach
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.core.MovementId

/** Frames a finished tape spent on one movement kind. */
data class TapeSegment(val movement: MovementId, val frames: Int)

internal class Solution(
    val segments: Int,
    val launchMargin: Int,
    val parameters: TerminalApproach,
    val frames: Int,
    val collisionEvents: Int,
    val anchor: ValueAnchor,
    val tailFrames: List<SimulatedTrajectoryFrame>,
) {
    /**
     * The decisions this solution was built from; see [PlanSegment]. Lazy because solutions
     * are produced speculatively and few are certified. See docs/decisions/improver.md.
     * Invariant: this is the actual certified decision chain, not a second route model.
     * [PlanImprover] re-runs the suffix and returns its complete winning solution;
     * [Certifier] publishes that chain without reconstructing it from graph segments.
     */
    val planSegments: List<PlanSegment> by lazy { segmentsOf(anchor, tailFrames, parameters) }

    /**
     * The whole tape, ancestry flattened. Lazy for the same reason: solutions are scored
     * and compared by the hundred and only the certified few are ever replayed.
     */
    val inputs: List<MovementSimulationInput> by lazy { anchor.prefix() + tailFrames.map { it.input } }

    /** Segment boundaries, the terminal's included. */
    val boundaries: List<Int> by lazy { anchor.boundaries() + anchor.elapsed }

    val score: Int get() = frames + COLLISION_FRAME_PENALTY * collisionEvents

    /** Frames per movement kind; the terminal run is attributed to the last anchor's movement. */
    fun segments(): List<TapeSegment> {
        val out = ArrayList<TapeSegment>()
        var node: ValueAnchor? = anchor
        while (node != null && node.parent != null) {
            out += TapeSegment(node.via ?: MovementId.WALK, node.inputs.size)
            node = node.parent
        }
        out.reverse()
        val tail = frames - out.sumOf { it.frames }
        if (tail > 0) out += TapeSegment(MovementId.WALK, tail)
        return out
    }

    companion object {
        /** The score's exchange rate: frames a collision event is worth. */
        internal const val COLLISION_FRAME_PENALTY = 4

        /**
         * The decision chain behind a finished tape, root first, plus its terminal. An
         * anchor with no decision is a brake tail and becomes a terminal too.
         */
        internal fun segmentsOf(
            anchor: ValueAnchor,
            tail: List<SimulatedTrajectoryFrame>,
            parameters: TerminalApproach,
        ): List<PlanSegment> {
            val chain = ArrayList<ValueAnchor>()
            var node: ValueAnchor? = anchor
            while (node?.parent != null) {
                chain += node
                node = node.parent
            }
            chain.reverse()

            val segments = ArrayList<PlanSegment>(chain.size + 1)
            chain.forEach { child ->
                val parent = child.parent ?: return@forEach
                val decision = child.decision
                segments += if (decision != null) {
                    PlanSegment.Move(
                        decision = decision,
                        points = child.points,
                        entry = parent.state,
                        exit = child.state,
                        inputs = child.inputs,
                        startFrame = parent.elapsed,
                    )
                } else {
                    PlanSegment.Terminal(
                        approach = parameters,
                        entry = parent.state,
                        exit = child.state,
                        inputs = child.inputs,
                        startFrame = parent.elapsed,
                    )
                }
            }
            if (tail.isNotEmpty()) {
                segments += PlanSegment.Terminal(
                    approach = parameters,
                    entry = anchor.state,
                    exit = tail.last().state,
                    inputs = tail.map { it.input },
                    startFrame = anchor.elapsed,
                )
            }
            return segments
        }

        fun of(
            anchor: ValueAnchor,
            tail: List<SimulatedTrajectoryFrame>,
            parameters: TerminalApproach,
            collisionEvents: Int,
        ): Solution = Solution(
            segments = anchor.depth() + 1,
            launchMargin = anchor.launchMargin,
            parameters = parameters,
            frames = anchor.elapsed + tail.size,
            collisionEvents = collisionEvents,
            anchor = anchor,
            tailFrames = tail,
        )
    }
}
