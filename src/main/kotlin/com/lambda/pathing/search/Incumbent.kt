package com.lambda.pathing.search

internal class Incumbent(
	private val publications: Publications,
	private val horizon: Horizon,
	private val publisher: Publisher,
	private val config: ValueFieldSearchConfig,
	private val cursorFrame: (() -> Int?)?,
) {
	var best: Solution? = null
		private set

	var fallback: Solution? = null
		private set

	var firstFrames = -1
		private set

	var found = 0
		private set

	private var sealed: Solution? = null

	val hasSolution: Boolean get() = best != null || sealed != null

	val arrival: Solution?
		get() = sealed?.let { published -> best?.takeIf { it.score <= published.score } ?: published }

	fun offer(solution: Solution) {
		if (!horizon.canReach(solution.anchor) || !publications.adoptable(solution.anchor)) return
		val incumbent = best
		val tip = publications.tip
		val requiredGain = if (tip != null && !solution.anchor.descendsFrom(tip)) OFF_TAPE_GAIN_FRAMES else 0
		val improves = when {
			incumbent == null -> true
			requiredGain > 0 -> solution.score <= incumbent.score - requiredGain
			else -> solution.score < incumbent.score
		}
		if (!improves) return
		if (firstFrames < 0) firstFrames = solution.frames
		found++
		best = solution
	}

	fun fallbackCovers(elapsed: Int): Boolean = fallback?.let { it.frames <= elapsed } == true

	fun offerFallback(solution: Solution) {
		val current = fallback
		if (current == null || solution.score < current.score) fallback = solution
	}

	fun dropUnadoptable() {
		val current = best ?: return
		if (!horizon.canReach(current.anchor) || !publications.adoptable(current.anchor)) best = null
	}

	fun dropBeyond(cut: ValueAnchor) {
		fun beyond(solution: Solution?) = solution != null && solution.anchor !== cut && solution.anchor.descendsFrom(cut)
		if (beyond(best)) best = null
		if (beyond(sealed)) sealed = null
		if (beyond(fallback)) fallback = null
	}

	fun clear() {
		best = null
		sealed = null
		fallback = null
	}

	fun ready(solution: Solution): Boolean {
		if (config.maxFinalCommitFrames <= 0) return true
		val committed = publications.tip?.elapsed ?: return true
		return solution.frames - committed <= config.maxFinalCommitFrames
	}

	fun nearEnd(solution: Solution): Boolean {
		val executing = cursorFrame?.invoke() ?: return true
		return solution.frames - executing <= config.finalizeArrivalFrames
	}

	fun publishFinal(solution: Solution): Boolean =
		publisher.publishFinal(solution).also { if (it) sealed = solution }

	fun seal(solution: Solution): Solution? = when {
		publishFinal(solution) -> null
		sealed == null -> solution
		else -> null
	}

	private companion object {
		const val OFF_TAPE_GAIN_FRAMES = SWAP_FLOOR_GAIN_TICKS.toInt()
	}
}
