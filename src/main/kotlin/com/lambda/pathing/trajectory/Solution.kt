package com.lambda.pathing.trajectory

import com.lambda.pathing.movement.TerminalApproach
import com.lambda.pathing.prediction.simulation.MovementSimulationInput
import com.lambda.pathing.core.MovementId

/** Frames a finished tape spent on one movement kind. */
data class TapeSegment(val movement: MovementId, val frames: Int)

internal class Solution(
    val inputs: List<MovementSimulationInput>,
    val boundaries: List<Int>,
    val segments: Int,
    val launchMargin: Int,
    val parameters: TerminalApproach,
    val frames: Int,
    val collisionEvents: Int,
    val anchor: ValueAnchor,
    val tailFrames: List<SimulatedTrajectoryFrame>,
) {
    /**
     * The decisions this solution was built from; see [PlanSegment].
     *
     * Lazy on purpose. A solution is produced speculatively -- every finish sweep and
     * every arrival builds one -- while at most one per publication is ever certified
     * into a plan. Building the chain eagerly walked every anchor on a hot path and cost
     * enough wall clock to fail `HorizonWalkProbeTest`'s deliberately-starved fixture,
     * where the body outran a search that no longer fit its step budget.
     */
    val planSegments: List<PlanSegment> by lazy { segmentsOf(anchor, tailFrames, parameters) }

    val score: Int get() = frames + COLLISION_FRAME_PENALTY * collisionEvents

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
         * The decision chain behind a finished tape, root first, plus its terminal.
         *
         * Walked from the leaf because that is the only direction anchors link, then
         * reversed. An anchor with no decision is a brake tail rather than a movement --
         * the search publishes those as safe stops -- so it becomes a terminal too.
         */
        private fun segmentsOf(
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
            inputs = anchor.prefix() + tail.map { it.input },
            boundaries = anchor.boundaries() + anchor.elapsed,
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
