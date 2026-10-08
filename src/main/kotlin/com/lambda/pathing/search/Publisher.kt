package com.lambda.pathing.search

import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.search.Publications.Companion.DIVERGENCE_MARGIN_FRAMES

internal class Publisher(
	private val searchConfig: SearchConfig,
	private val field: ValueField,
	private val frontier: Frontier,
	private val clock: SearchClock,
	private val publications: Publications,
	private val sink: ((MotionPlanResult.Success, Boolean) -> Unit)?,
	private val budget: ExpansionBudget,
	private val incumbent: Incumbent,
	private val finisher: Finisher,
	private val certifier: Certifier,
	private val probe: SearchProbe,
) {
	val enabled: Boolean get() = sink != null

	var commitAttempts = 0
		private set

	var refusals = 0
		private set

	private var expansionsAtPublish = 0
	private var finalScore = Int.MAX_VALUE
	private var tipAbandoned = false

	private data class CommitInputs(
		val tip: ValueAnchor?,
		val root: ValueAnchor?,
		val adopted: Long,
		val along: ValueAnchor?,
		val target: ValueAnchor,
	)

	private var refusedCommit: CommitInputs? = null

	fun forgetRefusedCommit() {
		refusedCommit = null
	}

	fun abandonTip() {
		tipAbandoned = true
		refusedCommit = null
	}

	fun publishPrefix(anchor: ValueAnchor) {
		if (sink == null) return
		val remaining = field.guide(anchor.stance)
		if (!remaining.isFinite() || remaining <= FINISH_VALUE_TICKS) return
		val cursor = publications.cursor()
		val tip = publications.tip
		if (anchor.elapsed > publicationCap(cursor ?: -1)) return refuse()
		if (!field.guide(anchor.stance, SpeedClass.STOPPED).isFinite()) return refuse()
		if (incumbent.best?.anchor?.descendsFrom(anchor) == false) return refuse()
		if (tip == null) {
			if (anchor.elapsed < searchConfig.horizonCommitFrames) return
			if (clock.elapsedMillis() < searchConfig.safePrefixDelayMillis) return
			if (budget.count < searchConfig.minCommitExpansions) return
			if (remaining <= FIRST_PUBLISH_MIN_REMAINING_TICKS) return
		} else {
			if (!publications.upToDate) return refuse()
			if (anchor.elapsed < tip.elapsed + searchConfig.horizonCommitFrames) return refuse()
			if (budget.count - expansionsAtPublish < searchConfig.minCommitExpansions) return refuse()
			val executing = cursor ?: return
			if (tip.elapsed - executing > searchConfig.horizonRunwayFrames) return
			if (!publishableOver(tip, anchor, executing)) return refuse()
		}
		if (!publishBraked(anchor)) refuse()
	}

	fun commit(urgent: Boolean, along: ValueAnchor? = null): Boolean {
		if (sink == null) return false
		val tip = publications.tip
		if (!urgent && budget.count - expansionsAtPublish < searchConfig.minCommitExpansions) return false
		if (!publications.upToDate) return false
		val executing = publications.cursor() ?: -1
		val floor = maxOf(frontier.root?.elapsed ?: 0, executing)
		val leaf = along?.takeIf { it.elapsed > floor }
		val target = leaf
			?: (if (frontier.hasParked) frontier.parkedEntries.minByOrNull { it.order } else frontier.bestLiveEntryDeeperThan(floor))?.anchor
			?: return false
		val inputs = CommitInputs(tip, frontier.root, publications.adopted(), leaf, target)
		if (inputs == refusedCommit) return false
		commitAttempts++
		if (tip != null && field.guideFor(tip) - field.guideFor(target) < MIN_COMMIT_PROGRESS_TICKS) return refuseCommit(inputs)
		val cap = publicationCap(executing)
		val (withinCap, beyondCap) = target.lineFrom(frontier.root).filter { it.elapsed > floor }.partition { it.elapsed <= cap }
		for (candidate in withinCap.asReversed() + beyondCap) {
			if (tip == null && field.guide(candidate.stance) <= FINISH_VALUE_TICKS) continue
			if (tip != null && !publishableOver(tip, candidate, executing)) {
				refusals++
				continue
			}
			if (publishBraked(candidate)) return true
		}
		return refuseCommit(inputs)
	}

	fun publishFinal(solution: Solution): Boolean {
		if (sink == null) return false
		val newest = publications.newest ?: return false
		val reference = tipArrival(newest.anchor)
		if (solution.score >= finalScore) return false
		if (finalScore != Int.MAX_VALUE && solution.score + SWAP_FLOOR_GAIN_TICKS > reference) return false
		if (!publications.adoptable(solution.anchor)) return false
		if (!publications.upToDate) return false
		val certified = certifier.certify(solution) as? MotionPlanResult.Success ?: return false
		val terminal = tailAnchor(solution.anchor, certified, solution.collisionEvents)
		finalScore = solution.score
		publish(terminal, terminal, certified, arrives = true)
		incumbent.seal(solution)
		return true
	}

	fun seal(solution: Solution): Solution? =
		solution.takeIf { !publishFinal(solution) && !incumbent.isSealed && publications.tip == null }

	private fun refuse() {
		refusals++
	}

	private fun refuseCommit(inputs: CommitInputs): Boolean {
		refusals++
		refusedCommit = inputs
		return false
	}

	private fun publicationCap(executing: Int): Int =
		maxOf(executing, 0) + searchConfig.horizonCommitFrames * PUBLISH_RUNWAY_CHUNKS

	private fun publishableOver(tip: ValueAnchor, candidate: ValueAnchor, executing: Int): Boolean {
		if (executing >= 0 && publications.divergence(candidate) < executing + DIVERGENCE_MARGIN_FRAMES) return false
		if (candidate.descendsFrom(tip)) return candidate.elapsed > tip.elapsed
		if (tip.descendsFrom(candidate)) return false
		if (executing >= 0 && candidate.elapsed < executing + searchConfig.horizonCommitFrames) return false
		if (tipAbandoned) return true
		return arrivalEstimate(candidate) + SWAP_FLOOR_GAIN_TICKS <= tipArrival(tip)
	}

	private fun tipArrival(tip: ValueAnchor): Double =
		incumbent.best?.takeIf { it.anchor.descendsFrom(tip) }?.score?.toDouble() ?: arrivalEstimate(tip, staleAllowance())

	private fun staleAllowance(): Double =
		((budget.count - expansionsAtPublish - STALE_TIP_FLOOR_EXPANSIONS).toDouble() / STALE_TIP_RAMP_EXPANSIONS)
			.coerceIn(0.0, 1.0) * STALE_TIP_MAX_TICKS

	private fun arrivalEstimate(anchor: ValueAnchor, allowance: Double = 0.0): Double =
		anchor.arrivalEstimate(field.guideFor(anchor), allowance)

	private fun publishBraked(anchor: ValueAnchor): Boolean {
		val braked = finisher.brakeToStop(anchor) ?: return false
		val certified = certifier.certify(braked) as? MotionPlanResult.Success ?: return false
		publish(anchor, tailAnchor(anchor, certified, anchor.collisionEvents), certified, arrives = false)
		return true
	}

	private fun publish(anchor: ValueAnchor, brake: ValueAnchor, certified: MotionPlanResult.Success, arrives: Boolean) {
		publications.record(anchor, brake, certified)
		expansionsAtPublish = budget.count
		refusedCommit = null
		tipAbandoned = false
		sink?.invoke(certified, arrives)
	}

	private fun tailAnchor(parent: ValueAnchor, certified: MotionPlanResult.Success, collisionEvents: Int): ValueAnchor {
		val frames = certified.plan.frames
		val tail = frames.drop(parent.elapsed)
		val terminal = frames.last().state
		return ValueAnchor(
			state = terminal,
			stance = stanceOf(terminal),
			elapsed = frames.size,
			collisionEvents = collisionEvents,
			inputSwitches = parent.inputSwitches,
			parent = parent,
			inputs = tail.map { it.input },
			trace = probe.traceOf(tail),
		)
	}

	private companion object {
		const val MIN_COMMIT_PROGRESS_TICKS = 1.0

		const val STALE_TIP_FLOOR_EXPANSIONS = 2000

		const val STALE_TIP_RAMP_EXPANSIONS = 4000

		const val STALE_TIP_MAX_TICKS = 30.0

		const val FIRST_PUBLISH_MIN_REMAINING_TICKS = 30.0

		const val PUBLISH_RUNWAY_CHUNKS = 3
	}
}
