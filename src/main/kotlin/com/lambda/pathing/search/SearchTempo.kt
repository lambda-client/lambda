package com.lambda.pathing.search

internal class SearchTempo {
	var expansionsPerFrame = 0.0
		private set

	private var cursorMark = -1
	private var expansionsAtCursorMark = 0

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

	fun starved(forkLife: Int, headroom: Int): Boolean =
		expansionsPerFrame > 0.0 && forkLife.toDouble() * expansionsPerFrame < headroom
}
