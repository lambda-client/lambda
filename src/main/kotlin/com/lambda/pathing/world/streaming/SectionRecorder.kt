package com.lambda.pathing.world.streaming

interface SectionRecorder {
	fun record(sectionKey: Long)

	fun flush() {}
}
