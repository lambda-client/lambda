package com.lambda.pathing.search

internal class ExpansionBudget(private val window: Int, private val clock: SearchClock) {
	var count = 0
		private set

	private var windowStart = 0

	val inWindow: Int get() = count - windowStart

	val exhausted: Boolean get() = inWindow >= window

	fun spend() {
		count++
		clock.onExpansion()
	}

	fun restartWindow() {
		windowStart = count
	}
}
