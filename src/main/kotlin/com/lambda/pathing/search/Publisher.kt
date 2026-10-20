package com.lambda.pathing.search

import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.MovementId
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
	private val live: Boolean,
) {
	val enabled: Boolean get() = sink != null

	var commitAttempts = 0
		private set

	var refusals = 0
		private set

	private enum class Refusal(val label: String) {
		CAP("cap"),
		UNSTOPPABLE("unstoppable"),
		OFF_INCUMBENT("off-incumbent"),
		UNJUDGED("unjudged"),
		SHORT("short"),
		TEMPO("tempo"),
		UNPUBLISHABLE("unpublishable"),
		NO_BRAKE("no-brake"),
		SUBMERGED("submerged"),
		CERTIFY("certify"),
		NO_PROGRESS("no-progress"),
		COMMIT("commit"),
	}

	private val refusalCounts = IntArray(Refusal.entries.size)

	val refusalReasons: Map<String, Int>
		get() = Refusal.entries.filter { refusalCounts[it.ordinal] > 0 }.associate { it.label to refusalCounts[it.ordinal] }

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

	private var deferredPlunge: ValueAnchor? = null

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
		if (anchor.elapsed > publicationCap(cursor ?: -1) && !plungesFromWithin(anchor, cursor ?: -1)) return refuse(Refusal.CAP)
		if (!field.guide(anchor.stance, SpeedClass.STOPPED).isFinite()) return refuse(Refusal.UNSTOPPABLE)
		if (incumbent.best?.anchor?.descendsFrom(anchor) == false) return refuse(Refusal.OFF_INCUMBENT)
		if (tip == null) {
			if (anchor.elapsed < searchConfig.horizonCommitFrames && (anchor.decision as? TrajectoryDecision.Fall)?.water != true) return
			if (!firstPublicationDue()) return deferPlunge(anchor)
			if (remaining <= FIRST_PUBLISH_MIN_REMAINING_TICKS) return
		} else {
			if (!publications.upToDate) return refuse(Refusal.UNJUDGED)
			if (anchor.elapsed < tip.elapsed + searchConfig.horizonCommitFrames) return refuse(Refusal.SHORT)
			if (incumbent.best == null && field.guideFor(tip) - field.guideFor(anchor) < MIN_COMMIT_PROGRESS_TICKS) return refuse(Refusal.NO_PROGRESS)
			if (budget.count - expansionsAtPublish < searchConfig.minCommitExpansions) return refuse(Refusal.TEMPO)
			val executing = cursor ?: return
			if (tip.elapsed - executing > searchConfig.horizonRunwayFrames) return
			if (!publishableOver(tip, anchor, executing)) return refuse(Refusal.UNPUBLISHABLE)
		}
		publishBraked(anchor)?.let(::refuse)
	}

	fun publishAfloat(root: ValueAnchor) {
		if (sink == null || publications.tip != null || !root.state.touchingWater || root.state.onGround) return
		val treading = finisher.treadTape(root) ?: return refuse(Refusal.NO_BRAKE)
		val certified = certifier.certify(treading) as? MotionPlanResult.Success ?: return refuse(Refusal.CERTIFY)
		publish(root, tailAnchor(root, certified, root.collisionEvents), certified, arrives = false)
	}

	fun publishDeferredPlunge() {
		val anchor = deferredPlunge ?: return
		if (!firstPublicationDue()) return
		deferredPlunge = null
		publishPrefix(anchor)
	}

	private fun firstPublicationDue(): Boolean =
		clock.elapsedMillis() >= searchConfig.safePrefixDelayMillis && budget.count >= searchConfig.minCommitExpansions

	private fun deferPlunge(anchor: ValueAnchor) {
		if (anchor.decision?.movement != MovementId.FALL) return
		val held = deferredPlunge
		if (held == null || arrivalEstimate(anchor) < arrivalEstimate(held)) deferredPlunge = anchor
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
		if (tip != null && field.guideFor(tip) - field.guideFor(target) < MIN_COMMIT_PROGRESS_TICKS) return refuseCommit(inputs, Refusal.NO_PROGRESS)
		val cap = publicationCap(executing)
		val (withinCap, beyondCap) = target.lineFrom(frontier.root).filter { it.elapsed > floor }.partition { it.elapsed <= cap }
		for (candidate in withinCap.asReversed() + beyondCap) {
			if (tip == null && field.guide(candidate.stance) <= FINISH_VALUE_TICKS) continue
			if (tip != null && !publishableOver(tip, candidate, executing)) {
				refuse(Refusal.UNPUBLISHABLE)
				continue
			}
			if (publishBraked(candidate) == null) return true
		}
		return refuseCommit(inputs, Refusal.COMMIT)
	}

	fun publishFinal(solution: Solution): Boolean {
		if (sink == null) return false
		val newest = publications.newest
		if (newest == null && !live) return false
		if (solution.score >= finalScore) return false
		if (newest != null && finalScore != Int.MAX_VALUE && solution.score + SWAP_FLOOR_GAIN_TICKS > tipArrival(newest.anchor)) return false
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

	private fun refuse(reason: Refusal) {
		refusals++
		refusalCounts[reason.ordinal]++
	}

	private fun refuseCommit(inputs: CommitInputs, reason: Refusal): Boolean {
		refuse(reason)
		refusedCommit = inputs
		return false
	}

	private fun publicationCap(executing: Int): Int =
		maxOf(executing, 0) + searchConfig.horizonCommitFrames * PUBLISH_RUNWAY_CHUNKS

	private fun plungesFromWithin(anchor: ValueAnchor, executing: Int): Boolean =
		anchor.decision?.movement == MovementId.FALL && (anchor.parent?.elapsed ?: Int.MAX_VALUE) <= publicationCap(executing)

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

	private fun publishBraked(anchor: ValueAnchor): Refusal? {
		val braked = finisher.brakeToStop(anchor) ?: return Refusal.NO_BRAKE
		if ((braked.tailFrames.lastOrNull()?.state ?: anchor.state).eyeInWater) return Refusal.SUBMERGED
		val certified = certifier.certify(braked) as? MotionPlanResult.Success ?: return Refusal.CERTIFY
		publish(anchor, tailAnchor(anchor, certified, anchor.collisionEvents), certified, arrives = false)
		return null
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
		var link = parent
		fun linkTo(until: Int) {
			val tail = frames.subList(link.elapsed, until)
			val state = tail.last().state
			link = ValueAnchor(
				state = state,
				stance = heldStanceOf(field.view, state),
				elapsed = until,
				collisionEvents = collisionEvents,
				inputSwitches = parent.inputSwitches,
				parent = link,
				inputs = tail.map { it.input },
				trace = probe.traceOf(tail),
				jumps = parent.jumps,
			)
		}
		if (!frames.last().state.onGround) {
			for (until in parent.elapsed + 1 until frames.size) {
				if (until - link.elapsed >= HOLD_LINK_FRAMES && field.floatsAt(frames[until - 1].state, parent.stance.y)) linkTo(until)
			}
		}
		linkTo(frames.size)
		return link
	}

	private companion object {
		const val MIN_COMMIT_PROGRESS_TICKS = 1.0

		const val STALE_TIP_FLOOR_EXPANSIONS = 2000

		const val STALE_TIP_RAMP_EXPANSIONS = 4000

		const val STALE_TIP_MAX_TICKS = 30.0

		const val FIRST_PUBLISH_MIN_REMAINING_TICKS = 30.0

		const val PUBLISH_RUNWAY_CHUNKS = 3

		const val HOLD_LINK_FRAMES = 10
	}
}
