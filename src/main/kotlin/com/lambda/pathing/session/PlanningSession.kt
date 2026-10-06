package com.lambda.pathing.session

import com.lambda.pathing.search.ExecutionFeedback

internal class PlanningSession(val generation: Long) : ExecutionFeedback {
	@Volatile
	var isCancelled = false
		private set

	var launchRevision: Long = -1L

	@Volatile
	private var executionFrame: Int? = null

	@Volatile
	private var adopted: Long = 0L

	fun updateExecutionFrame(frame: Int?) {
		executionFrame = frame
	}

	fun adopt(frame: Int, sequence: Long) {
		executionFrame = frame
		adopted = sequence
	}

	override fun cursorFrame(): Int? = executionFrame

	override fun adoptedSequence(): Long = adopted

	fun cancel() {
		isCancelled = true
	}
}
