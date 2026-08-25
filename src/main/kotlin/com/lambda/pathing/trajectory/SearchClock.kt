package com.lambda.pathing.trajectory

interface SearchClock {
    fun elapsedMillis(): Long

    fun onExpansion() {}
}

class SystemSearchClock : SearchClock {
    private val startedNanos = System.nanoTime()

    override fun elapsedMillis(): Long = (System.nanoTime() - startedNanos) / 1_000_000
}
