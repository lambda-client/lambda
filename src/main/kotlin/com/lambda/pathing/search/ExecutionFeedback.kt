package com.lambda.pathing.search

interface ExecutionFeedback {
	fun cursorFrame(): Int?

	fun adoptedSequence(): Long

	fun refusedSequence(): Long

	companion object {
		val DETACHED: ExecutionFeedback = object : ExecutionFeedback {
			override fun cursorFrame(): Int? = null

			override fun adoptedSequence(): Long = Long.MAX_VALUE

			override fun refusedSequence(): Long = 0L
		}
	}
}
