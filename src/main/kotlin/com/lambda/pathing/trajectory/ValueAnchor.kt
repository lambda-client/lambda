package com.lambda.pathing.trajectory

import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.BodyState
import com.lambda.pathing.movement.PricedDecision
import com.lambda.pathing.movement.TrajectoryDecision
import com.lambda.pathing.prediction.simulation.MovementSimulationInput
import com.lambda.pathing.prediction.simulation.MovementSimulationState

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
    /**
     * The decision whose rollout produced this anchor, for attribution only.
     *
     * The search's own cost accounting is in frames, which says how expensive a tape is
     * but not what made it expensive. Carrying the movement lets a finished tape be split
     * by what the body was doing, and that split compared against the coarse route's
     * admissible lower bound -- the only denominator in the system that says what the
     * motion *should* have cost.
     */
    var via: com.lambda.pathing.core.MovementId? = null

    /**
     * The decision this anchor's inputs came from, and the guide chain it was built
     * against. Together they re-run the segment from any entry state; see [PlanSegment].
     * Null on the root and on brake tails, which are terminals rather than movements.
     */
    var decision: com.lambda.pathing.movement.TrajectoryDecision? = null

    var points: List<com.lambda.pathing.core.HorizontalPoint> = emptyList()

    var actions: List<PricedDecision>? = null

    var actionsHazardFrame: Int? = null

    var actionsEpoch: Int = -1

    /**
     * Queue penalty for the cheapest movement this anchor has left to try.
     *
     * Zero until the anchor is first expanded, which keeps its admission optimistic --
     * the vocabulary is not built until the anchor is actually polled, and guessing high
     * would bury a good anchor before anything was known about it. From the first
     * expansion on it is the honest price of continuing here, so an anchor whose cheap
     * options are used up sinks past a fresh one whose next move is a plain walk.
     */
    var pendingSurcharge: Double = 0.0

    val attempted: MutableSet<TrajectoryDecision> = HashSet()

    val familyPrefixFailures: MutableMap<Any, Int> = HashMap()

    override var hazardFrame: Int? = null

    var sweptEpoch: Int = -1

    /**
     * Cached frame where this anchor's tape departs the acked running tape, valid
     * while [divergenceSequence] matches the publication it was computed against.
     * Divergence is fixed per publication, and the liveness check runs at poll time --
     * uncached it is a lineage walk per poll.
     */
    var divergenceElapsed: Int = -1
    var divergenceSequence: Long = Long.MIN_VALUE

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
