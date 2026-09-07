package com.lambda.pathing.search

import com.lambda.pathing.core.Stance
import com.lambda.pathing.actions.BodyState
import com.lambda.pathing.actions.PricedDecision
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.MovementId

internal class ValueAnchor(
    override val state: MovementSimulationState,
    override val stance: Stance,
    val elapsed: Int,
    val collisionEvents: Int,
    val launchMargin: Int,
    val inputSwitches: Int,
    val parent: ValueAnchor?,
    val inputs: List<MovementSimulationInput>,
    val boundary: Int,
) : BodyState {
    /** Horizontal speed, fixed with the state: read on every queue insertion and beam comparison. */
    override val speed: Double = state.velocity.horizontalLength()

    /** The movement whose rollout produced this anchor, for attribution only (see [TapeSegment]). */
    var via: MovementId? = null

    /**
     * The decision this anchor's inputs came from, and the guide chain it was built
     * against. Together they re-run the segment from any entry state; see [PlanSegment].
     * Null on the root and on brake tails, which are terminals rather than movements.
     */
    var decision: TrajectoryDecision? = null

    var points: List<HorizontalPoint> = emptyList()

    /** Decimated body positions of the rollout that produced this anchor; filled only while a tree or candidate view is drawn. */
    var trace: List<net.minecraft.util.math.Vec3d> = emptyList()

    companion object {
        /** Every [TRACE_STRIDE]th interior body position of [frames]: the shape of a rollout for the tree view. */
        fun traceOf(frames: List<SimulatedTrajectoryFrame>): List<net.minecraft.util.math.Vec3d> {
            if (frames.size <= TRACE_STRIDE + 1) return emptyList()
            val trace = ArrayList<net.minecraft.util.math.Vec3d>(frames.size / TRACE_STRIDE + 1)
            for (i in TRACE_STRIDE until frames.size - 1 step TRACE_STRIDE) trace += frames[i].state.position
            return trace
        }

        private const val TRACE_STRIDE = 3
    }

    var actions: List<PricedDecision>? = null

    var actionsHazardFrame: Int? = null

    var actionsEpoch: Int = -1

    /**
     * Queue penalty for the cheapest movement this anchor has left to try; zero until first
     * expanded (the vocabulary is not built before the anchor is polled). See docs/decisions/beam.md.
     */
    var pendingSurcharge: Double = 0.0

    val attempted: MutableSet<TrajectoryDecision> = HashSet()

    val familyPrefixFailures: MutableMap<Any, Int> = HashMap()

    override var hazardFrame: Int? = null

    var sweptEpoch: Int = -1

    /**
     * Cached frame where this anchor's tape departs the acked running tape, valid while
     * [divergenceSequence] matches the publication it was computed against.
     */
    var divergenceElapsed: Int = -1
    var divergenceSequence: Long = Long.MIN_VALUE

    /**
     * The frontier's momentum credit and turn cost toward the next route cell, valid while
     * [momentumEpoch] matches the frontier's field epoch; credit is NaN when the field
     * offers no next cell. Two terms, not their sum: the order is a left-to-right
     * floating-point expression and must stay bit-identical.
     */
    var momentumEpoch: Int = -1
    var momentumCredit: Double = Double.NaN
    var momentumTurnCost: Double = 0.0

    fun prefix(): List<MovementSimulationInput> {
        val chain = ArrayList<List<MovementSimulationInput>>()
        var node: ValueAnchor? = this
        while (node != null) {
            if (node.inputs.isNotEmpty()) chain += node.inputs
            node = node.parent
        }
        chain.reverse()
        return chain.flatten()
    }

    fun boundaries(): List<Int> {
        val frames = ArrayList<Int>()
        var node: ValueAnchor? = this
        while (node?.parent != null) {
            frames += node.boundary
            node = node.parent
        }
        return frames.asReversed().dropLast(1)
    }

    fun depth(): Int {
        var depth = 0
        var node: ValueAnchor? = this
        while (node?.parent != null) {
            depth++
            node = node.parent
        }
        return depth
    }

    fun descendsFrom(other: ValueAnchor): Boolean {
        var node: ValueAnchor? = this
        while (node != null) {
            if (node === other) return true
            node = node.parent
        }
        return false
    }

    fun hasVisited(visited: Stance): Boolean {
        var node: ValueAnchor? = this
        while (node != null) {
            if (node.stance == visited) return true
            node = node.parent
        }
        return false
    }
}
