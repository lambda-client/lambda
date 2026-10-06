package com.lambda.pathing.search

interface ExecutionFeedback {
	fun cursorFrame(): Int?

	fun adoptedSequence(): Long

	companion object {
		val DETACHED: ExecutionFeedback = object : ExecutionFeedback {
			override fun cursorFrame(): Int? = null

			override fun adoptedSequence(): Long = Long.MAX_VALUE
		}
	}
}
