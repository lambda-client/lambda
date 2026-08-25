package com.lambda.pathing.trajectory

class VirtualSearchClock(private val microsPerExpansion: Long = DEFAULT_MICROS_PER_EXPANSION) : SearchClock {
    private var expansions = 0L

    override fun elapsedMillis(): Long = expansions * microsPerExpansion / 1_000

    override fun onExpansion() {
        expansions++
    }

    fun cursorFrame(): Int = (elapsedMillis() / MILLIS_PER_TICK).toInt()

    companion object {
        const val DEFAULT_MICROS_PER_EXPANSION = 640L
        private const val MILLIS_PER_TICK = 50L
    }
}
