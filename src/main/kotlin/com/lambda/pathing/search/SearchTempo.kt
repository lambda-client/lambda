package com.lambda.pathing.search

/**
 * Expansions per executed body frame (slow EMA): converts a fork's remaining life from
 * frames into search budget, so headroom is denominated in expansions rather than
 * frames. See docs/decisions/tempo-law.md.
 */
internal class SearchTempo {
    var expansionsPerFrame = 0.0
        private set

    private var cursorMark = -1
    private var expansionsAtCursorMark = 0

    /** One cursor reading: fold the expansions spent since the mark into the EMA, then move the mark. */
    fun observe(executing: Int, expansions: Int) {
        if (cursorMark in 0 until executing) {
            val perFrame = (expansions - expansionsAtCursorMark).toDouble() / (executing - cursorMark)
            expansionsPerFrame =
                if (expansionsPerFrame == 0.0) perFrame
                else 0.7 * expansionsPerFrame + 0.3 * perFrame
        }
        if (executing != cursorMark) {
            cursorMark = executing
            expansionsAtCursorMark = expansions
        }
    }

    /**
     * Whether a branch with [forkLife] frames left cannot be deepened by [headroom]
     * expansions before execution forecloses it. Never starves before the tempo is known.
     */
    fun starved(forkLife: Int, headroom: Int): Boolean =
        expansionsPerFrame > 0.0 && forkLife.toDouble() * expansionsPerFrame < headroom
}
