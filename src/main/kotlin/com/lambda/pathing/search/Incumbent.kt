package com.lambda.pathing.search

internal class Incumbent(
	private val publications: Publications,
	private val frontier: Frontier,
	private val config: SearchConfig,
	private val execution: ExecutionFeedback,
	private val budget: ExpansionBudget,
) {
	var best: Solution? = null
		private set

	var fallback: Solution? = null
		private set

	private var improvedAt = 0

	var firstFrames = -1
		private set

	var found = 0
		private set

	private var sealed: Solution? = null

	val hasSolution: Boolean get() = best != null || sealed != null

	val stalled: Boolean get() = budget.count - improvedAt >= config.stallExpansions

	val arrival: Solution?
		get() = sealed?.let { published -> best?.takeIf { it.score <= published.score } ?: published }

	fun offer(solution: Solution) {
		if (!viable(solution)) return
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
		improvedAt = budget.count
	}

	fun fallbackCovers(elapsed: Int): Boolean = fallback?.let { it.frames <= elapsed } == true

	fun offerFallback(solution: Solution) {
		val current = fallback
		if (current == null || solution.score < current.score) fallback = solution
	}

	fun dropUnadoptable() {
		val current = best ?: return
		if (!viable(current)) best = null
	}

	private fun viable(solution: Solution): Boolean =
		frontier.canReach(solution.anchor) && publications.adoptable(solution.anchor)

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
		improvedAt = budget.count
	}

	fun ready(solution: Solution): Boolean {
		if (config.maxFinalCommitFrames <= 0) return true
		val committed = publications.tip?.elapsed ?: return true
		return solution.frames - committed <= config.maxFinalCommitFrames
	}

	fun nearEnd(solution: Solution): Boolean {
		val executing = execution.cursorFrame() ?: return true
		return solution.frames - executing <= FINALIZE_ARRIVAL_FRAMES
	}

	val isSealed: Boolean get() = sealed != null

	fun seal(solution: Solution) {
		sealed = solution
	}

	private companion object {
		const val OFF_TAPE_GAIN_FRAMES = SWAP_FLOOR_GAIN_TICKS.toInt()
		const val FINALIZE_ARRIVAL_FRAMES = 10
	}
}
