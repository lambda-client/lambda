package com.lambda.pathing.coarse

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance

/**
 * The velocity dimension the coarse graph never had.
 *
 * Two classes, deliberately: a body is either carrying momentum or it is not, and that
 * single bit is what every measured blind spot of the flat graph reduces to -- momentum
 * through corners, jump chains priced as independent hops, the transition tax charged
 * to motion that never stops. Finer classes (headings, speed bands) only earn their
 * node-count once this bit has been measured to pay.
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
    /**
     * Stable across JVMs, deliberately: an enum's default hashCode is its identity
     * hash, which reorders every HashMap of momentum nodes -- and with them the D*
     * successor iteration and its tie-breaks -- differently on every run. Measured as
     * the same baseline walk landing on 364 or 369 frames from one JVM to the next.
     */
    override fun hashCode(): Int = 31 * stance.hashCode() + speed.ordinal
}

/**
 * Class-conditioned edges over the same stance-level move library.
 *
 * The rules are the honest half of the §49 fossil: the one-tick transition tax models
 * decision friction a MOVING body chaining decisions does not pay, so MOVING departures
 * shed it; a STOPPED body pays the tax AND its acceleration back to cruise. Precision
 * movements bound the classes: a climb must be entered from rest, a climb or step-up
 * exits at rest, and a moving body converts to rest through an explicit brake edge --
 * which is also how arriving at the goal prices its stop exactly once.
 */
internal object MomentumRules {

    /** Ticks a body departing from rest spends short of cruise across its first edge. */
    const val STARTUP_TICKS = 2.0

    /** The brake self-edge: sprint to standstill, measured as a handful of friction ticks. */
    const val BRAKE_TICKS = 4.0

    /** The per-edge transition tax a chained MOVING body does not pay. */
    const val CHAIN_TAX_TICKS = 1.0

    fun departsStoppedOnly(movement: MovementId): Boolean = movement == MovementId.CLIMB

    fun arrivesStopped(movement: MovementId): Boolean =
        movement == MovementId.CLIMB || movement == MovementId.STEP_UP

    private fun movingCost(ticks: Double): Double =
        (ticks - CHAIN_TAX_TICKS).coerceAtLeast(ticks * 0.5)

    private fun stoppedCost(movement: MovementId, ticks: Double): Double =
        if (departsStoppedOnly(movement)) ticks else ticks + STARTUP_TICKS

    fun successors(
        edges: CoarseEdgeCache,
        node: MomentumStance,
    ): Map<MomentumStance, Double> {
        // Insertion-ordered on purpose: these maps drive the D* successor iteration,
        // and a hash-ordered map's treeified bins break equal-hash ties by identity
        // hash -- run-to-run nondeterminism measured as the same walk landing on 360,
        // 364 or 371 frames across JVMs.
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
