package com.lambda.pathing.search

import com.lambda.pathing.actions.DecisionPrice

internal class Temperature(private val ceiling: Double) {
	var current = INITIAL
		private set

	val exhausted: Boolean get() = current >= ceiling

	fun affords(price: DecisionPrice): Boolean = price.difficulty <= current

	fun surcharge(price: DecisionPrice): Double = price.ticks + RISK_WEIGHT * price.difficulty

	fun raise(): Boolean {
		if (exhausted) return false
		current = (current * GROWTH).coerceAtMost(ceiling)
		return true
	}

	fun cool(): Boolean {
		if (current <= INITIAL) return false
		current = (current / GROWTH).coerceAtLeast(INITIAL)
		return true
	}

	private companion object {

		const val INITIAL = 0.3

		const val GROWTH = 1.6

		const val RISK_WEIGHT = 4.0
	}
}
