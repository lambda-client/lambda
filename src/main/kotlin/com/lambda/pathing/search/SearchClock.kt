package com.lambda.pathing.search

interface SearchClock {
	fun elapsedMillis(): Long

	fun onExpansion() {}

	fun onWait(millis: Long) {}

	val simulated: Boolean get() = false
}

class SystemSearchClock : SearchClock {
	private val startedNanos = System.nanoTime()

	override fun elapsedMillis(): Long = (System.nanoTime() - startedNanos) / 1_000_000
}
