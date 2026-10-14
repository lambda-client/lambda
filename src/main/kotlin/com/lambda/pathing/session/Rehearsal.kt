package com.lambda.pathing.session

import com.lambda.pathing.core.Stance
import com.lambda.pathing.search.PublishedPath

data class RehearsalPoint(val millis: Long, val frames: Int, val label: String)

data class RehearsalView(val history: List<RehearsalPoint>, val progress: String, val finished: Boolean)

internal class Rehearsal(val waypoints: List<Stance>) {
	@Volatile
	var history: List<RehearsalPoint> = emptyList()
		private set

	@Volatile
	var best: PublishedPath? = null
		private set

	@Volatile
	var progress: String = "planning the spine"

	@Volatile
	var finished = false

	var performRequested = false

	@Volatile
	var performing = false

	var restarts = 0

	val bestFrames: Int? get() = best?.plan?.tape?.frameCount

	fun view(): RehearsalView = RehearsalView(history, progress, finished)

	fun record(point: RehearsalPoint, path: PublishedPath): Boolean {
		val current = best
		if (current != null && path.plan.tape.frameCount >= current.plan.tape.frameCount) return false
		best = path
		history = history + point
		return true
	}
}
