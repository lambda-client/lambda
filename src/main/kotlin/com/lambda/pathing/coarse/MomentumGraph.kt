package com.lambda.pathing.coarse

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance

/**
 * The coarse graph's velocity dimension: a body is either carrying momentum or it is not.
 * Finer classes (headings, speed bands) must earn their node count against this bit.
 */
enum class SpeedClass {
    STOPPED, MOVING;

    companion object {
        /** Below this a body's momentum is not worth conditioning plans on. */
        const val MOVING_THRESHOLD_BLOCKS_PER_TICK = 0.15

        fun of(speed: Double): SpeedClass =
            if (speed >= MOVING_THRESHOLD_BLOCKS_PER_TICK) MOVING else STOPPED
    }
}

data class MomentumStance(val stance: Stance, val speed: SpeedClass) {
    /** Ordinal, not enum identity hash: JVM-stable map order; see docs/decisions/determinism.md. */
    override fun hashCode(): Int = 31 * stance.hashCode() + speed.ordinal
}

/**
 * Class-conditioned edges over the same stance-level move library.
 *
 * The one-tick transition tax (see docs/decisions/transition-overhead.md) models
 * decision friction a MOVING body chaining decisions does not pay, so MOVING departures
 * shed it; a STOPPED body pays the tax AND its acceleration back to cruise. Precision
 * movements bound the classes: a climb must be entered from rest, a climb or step-up
 * exits at rest, and a moving body converts to rest through an explicit brake edge --
 * which is also how arriving at the goal prices its stop exactly once.
 */
internal object MomentumRules {

    /** Ticks a body departing from rest spends short of cruise across its first edge. */
    const val STARTUP_TICKS = 2.0

    /** The brake self-edge: sprint to standstill. */
    const val BRAKE_TICKS = 4.0

    /** The per-edge transition tax a chained MOVING body does not pay; see docs/decisions/transition-overhead.md. */
    const val CHAIN_TAX_TICKS = 1.0

    fun departsStoppedOnly(movement: MovementId): Boolean = movement == MovementId.CLIMB

    fun arrivesStopped(movement: MovementId): Boolean =
        movement == MovementId.CLIMB || movement == MovementId.STEP_UP ||
            // Grabbing a ladder kills the flight's momentum: the caught body hangs.
            movement == MovementId.LADDER_CATCH

    private fun movingCost(ticks: Double): Double =
        (ticks - CHAIN_TAX_TICKS).coerceAtLeast(ticks * 0.5)

    private fun stoppedCost(movement: MovementId, ticks: Double): Double =
        if (departsStoppedOnly(movement)) ticks else ticks + STARTUP_TICKS

    fun successors(
        edges: CoarseEdgeCache,
        node: MomentumStance,
    ): Map<MomentumStance, Double> {
        // Insertion-ordered: drives the D* successor iteration; see docs/decisions/determinism.md.
        val out = LinkedHashMap<MomentumStance, Double>()
        if (node.speed == SpeedClass.MOVING) {
            out[MomentumStance(node.stance, SpeedClass.STOPPED)] = BRAKE_TICKS
        }
        for (edge in edges.edgesFrom(node.stance)) {
            if (node.speed == SpeedClass.MOVING && departsStoppedOnly(edge.movement)) continue
            val arrive = if (arrivesStopped(edge.movement)) SpeedClass.STOPPED else SpeedClass.MOVING
            val cost = when (node.speed) {
                SpeedClass.MOVING -> movingCost(edge.lowerBoundTicks)
                SpeedClass.STOPPED -> stoppedCost(edge.movement, edge.lowerBoundTicks)
            }
            out.merge(MomentumStance(edge.to, arrive), cost, ::minOf)
        }
        return out
    }

    fun predecessors(
        edges: CoarseEdgeCache,
        node: MomentumStance,
    ): Map<MomentumStance, Double> {
        val out = LinkedHashMap<MomentumStance, Double>()
        if (node.speed == SpeedClass.STOPPED) {
            out[MomentumStance(node.stance, SpeedClass.MOVING)] = BRAKE_TICKS
        }
        for (edge in edges.edgesTo(node.stance)) {
            val arrive = if (arrivesStopped(edge.movement)) SpeedClass.STOPPED else SpeedClass.MOVING
            if (arrive != node.speed) continue
            if (!departsStoppedOnly(edge.movement)) {
                out.merge(
                    MomentumStance(edge.from, SpeedClass.MOVING),
                    movingCost(edge.lowerBoundTicks),
                    ::minOf,
                )
            }
            out.merge(
                MomentumStance(edge.from, SpeedClass.STOPPED),
                stoppedCost(edge.movement, edge.lowerBoundTicks),
                ::minOf,
            )
        }
        return out
    }
}
