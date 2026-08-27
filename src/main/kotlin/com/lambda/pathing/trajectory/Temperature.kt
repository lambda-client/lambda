package com.lambda.pathing.trajectory

import com.lambda.pathing.movement.DecisionPrice

/**
 * How much difficulty the search is currently willing to buy.
 *
 * The search starts cold: only movements that reliably work are offered, so the first
 * tape is found on plain walking and wide-margin jumps, fast. Evidence of a stall heats
 * it -- a tight landing or a run-up becomes affordable, and the terrain that genuinely
 * needs one gets one. Real progress cools it again, so the next stretch of easy ground
 * is not searched at parkour prices.
 *
 * There is no randomness here despite the name. Temperature is an affordability
 * threshold, and the annealing is over the *vocabulary*, not over the acceptance of
 * worse solutions: a hotter search can reach shortcuts a colder one cannot, which is
 * what makes the tape get shorter the longer the search runs.
 *
 * [surcharge] is deliberately independent of the current level. A decision's standing in
 * the queue is a fixed property of the decision, so heating admits new options at the
 * back of the order rather than reshuffling the ones already there.
 */
internal class Temperature(private var level: Double = INITIAL) {

    val current: Double get() = level

    val exhausted: Boolean get() = level >= 1.0

    /** Whether this decision is offered at the current level. */
    fun affords(price: DecisionPrice): Boolean = price.difficulty <= level

    /** Ticks added to the frontier order for choosing this decision next. */
    fun surcharge(price: DecisionPrice): Double = price.ticks + RISK_WEIGHT * price.difficulty

    /** Stalled: buy access to harder movements. Returns false once nothing is left to unlock. */
    fun raise(): Boolean {
        if (exhausted) return false
        level = (level * GROWTH).coerceAtMost(1.0)
        return true
    }

    /**
     * The guide moved: step back toward preferring movements that reliably work.
     *
     * One step, not a reset. Difficulty is a property of terrain, and terrain arrives in
     * runs -- a parkour section is a dozen gaps, not one. Resetting to cold after each
     * gap made the search re-learn the same lesson at every anchor and, because the
     * horizon commits as it goes, walk around gaps it had already proved it could jump:
     * tapes measured 8% longer for it. Cooling gradually keeps a hot stretch hot and
     * still lets a long easy one settle back to cheap.
     */
    fun cool(): Boolean {
        if (level <= INITIAL) return false
        level = (level / GROWTH).coerceAtLeast(INITIAL)
        return true
    }

    private companion object {

        /**
         * Cold enough to exclude heading fans, tight landings and every run-up; warm
         * enough for plain walking and any jump the launch solver found room in.
         */
        const val INITIAL = 0.3

        /** Four steps from cold to everything: 0.30, 0.48, 0.77, 1.0. */
        const val GROWTH = 1.6

        /**
         * Ticks of queue penalty for a movement at the edge of the physics window.
         *
         * Sized against the coarse walk rate: roughly one block of walking, so a risky
         * attempt is tried after any safe one that gets a block closer, and before a safe
         * one that does not.
         */
        const val RISK_WEIGHT = 4.0
    }
}
