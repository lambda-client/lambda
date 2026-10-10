package com.lambda.pathing.search

class VirtualSearchClock(private val microsPerExpansion: Long = DEFAULT_MICROS_PER_EXPANSION) : SearchClock, ExecutionFeedback {
    private var expansions = 0L
    private var stalledMillis = 0L

    override fun elapsedMillis(): Long = expansions * microsPerExpansion / 1_000 + stalledMillis

    /** Lets [ticks] pass with no expansion, as when the planner thread is starved while the body keeps replaying. */
    fun stall(ticks: Int) {
        stalledMillis += ticks * MILLIS_PER_TICK
    }

    override fun onExpansion() {
        expansions++
    }

    override fun cursorFrame(): Int = (elapsedMillis() / MILLIS_PER_TICK).toInt()

    override fun adoptedSequence(): Long = Long.MAX_VALUE

    companion object {
        const val DEFAULT_MICROS_PER_EXPANSION = 640L
        private const val MILLIS_PER_TICK = 50L
    }
}
