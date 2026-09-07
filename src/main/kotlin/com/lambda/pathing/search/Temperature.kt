package com.lambda.pathing.search

import com.lambda.pathing.actions.DecisionPrice

/**
 * How much movement difficulty the search currently affords. An affordability threshold
 * over the vocabulary, not randomness: stalls raise it, guide progress cools it one step.
 * [surcharge] is independent of the level so heating appends options at the back of the
 * order rather than reshuffling it. See docs/decisions/annealing.md.
 */
internal class Temperature(
    private var level: Double = INITIAL,
    private val ceiling: Double = 1.0,
) {
    val current: Double get() = level

    val exhausted: Boolean get() = level >= ceiling

    /** Whether this decision is offered at the current level. */
    fun affords(price: DecisionPrice): Boolean = price.difficulty <= level

    /** Ticks added to the frontier order for choosing this decision next. */
    fun surcharge(price: DecisionPrice): Double = price.ticks + RISK_WEIGHT * price.difficulty

    /** Stalled: buy access to harder movements. Returns false once nothing is left to unlock. */
    fun raise(): Boolean {
        if (exhausted) return false
        level = (level * GROWTH).coerceAtMost(ceiling)
        return true
    }

    /** The guide moved: step down one tier, not to cold. Returns false at [INITIAL]. */
    fun cool(): Boolean {
        if (level <= INITIAL) return false
        level = (level / GROWTH).coerceAtLeast(INITIAL)
        return true
    }

    private companion object {
        /** Cold: plain walking and any jump the launch solver found room in; no fans, tight landings or run-ups. */
        const val INITIAL = 0.3

        /** Four steps from cold to everything: 0.30, 0.48, 0.77, 1.0. */
        const val GROWTH = 1.6

        /** Ticks of queue penalty at full difficulty: about one block of walking. */
        const val RISK_WEIGHT = 4.0
    }
}
