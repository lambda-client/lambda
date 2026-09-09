package com.lambda.pathing.search

import com.lambda.pathing.actions.DecisionPrice

internal class Temperature(
	private var level: Double = INITIAL,
	private val ceiling: Double = 1.0,
) {
	val current: Double get() = level

	val exhausted: Boolean get() = level >= ceiling

	fun affords(price: DecisionPrice): Boolean = price.difficulty <= level

	fun surcharge(price: DecisionPrice): Double = price.ticks + RISK_WEIGHT * price.difficulty

	fun raise(): Boolean {
		if (exhausted) return false
		level = (level * GROWTH).coerceAtMost(ceiling)
		return true
	}

	fun cool(): Boolean {
		if (level <= INITIAL) return false
		level = (level / GROWTH).coerceAtLeast(INITIAL)
		return true
	}

	private companion object {

		const val INITIAL = 0.3

		const val GROWTH = 1.6

		const val RISK_WEIGHT = 4.0
	}
}
