package com.lambda.pathing.session

import com.lambda.pathing.search.ExecutionFeedback

internal class PlanningSession(attached: Boolean = true) : ExecutionFeedback {
	@Volatile
	var isCancelled = false
		private set

	var launchRevision: Long = -1L

	@Volatile
	private var attached = attached

	@Volatile
	private var executionFrame: Int? = null

	@Volatile
	private var adopted: Long = 0L

	@Volatile
	private var refused: Long = 0L

	fun attach() {
		attached = true
	}

	fun updateExecutionFrame(frame: Int?) {
		executionFrame = frame
	}

	fun adopt(frame: Int, sequence: Long) {
		executionFrame = frame
		adopted = sequence
	}

	fun refuse(sequence: Long) {
		refused = maxOf(refused, sequence)
	}

	override fun cursorFrame(): Int? = if (attached) executionFrame else null

	override fun adoptedSequence(): Long = if (attached) adopted else Long.MAX_VALUE

	override fun refusedSequence(): Long = if (attached) refused else 0L

	fun cancel() {
		isCancelled = true
	}
}
