package com.lambda.pathing.search

import com.lambda.pathing.actions.TrajectoryDecision

/**
 * Shortens a solution by crossing a costly spine span, re-running its remaining
 * decisions from the rejoin (re-solving any that fail from the displaced body) and
 * finishing from the new body. A rejoin is any faster arrival on a spine cell: no state
 * tolerance is applied, because re-running the tail is the only test that matters and a
 * tolerance measured against it only discarded splices (docs/decisions/improver.md). Candidates are complete anchor chains: accepting one needs no
 * second route reconstruction. See docs/decisions/improver.md.
 */
internal class PlanImprover(
	private val rollouts: AnchorRollout,
	private val vocabulary: ActionSet,
	private val finisher: FinishPlanner,
	/** Whether a cut point is still adoptable; a splice behind the cursor can never publish. */
	private val canReach: (ValueAnchor) -> Boolean = { true },
	private val temperature: Temperature = Temperature(level = 1.0),
) {
	var rolloutsSpent = 0
		private set
	var splices = 0
		private set

	/** Where attempts die, for telling "no shortcut exists" from "the tail never survives". */
	var spansTried = 0
		private set
	var spansUnreachable = 0
		private set
	var crossingsFound = 0
		private set

	/** Rollouts that anchored, those that anchored on a spine cell, and cell hits that were not faster than the spine. */
	var landed = 0
		private set
	var cellHits = 0
		private set
	var gainless = 0
		private set
	var tailsSurvived = 0
		private set

	/** Spine decisions that failed from the displaced body and were replaced by a fresh solution for the same edge. */
	var resolvedDecisions = 0
		private set

	/** Spine decisions that replayed fine but were beaten to their cell by a fresh solution. */
	var reshapedDecisions = 0
		private set

	/** How tail attempts that did not meet the spine ended: rejection diagnostic class, blocked, arrived, or anchored off the spine. */
	val tailMisses = java.util.TreeMap<String, Int>()

	/** Tail landings that met the spine at a later junction than the decision's own cell. */
	var skippedAhead = 0
		private set
	var alternatesOffered = 0
		private set

	/** Tails that reached the last junction earlier than the spine, and how the finish then compared. */
	var fasterTails = 0
		private set
	var finishFailed = 0
		private set
	var finishSlower = 0
		private set

	/** Over the finishSlower cases: frames the tail had gained at the last junction, and frames the finish then cost beyond the spine's. */
	var finishGainLost = 0
		private set
	var finishExtraFrames = 0
		private set
	var finishCollisions = 0
		private set

	/** Where tails die: the class of the decision that failed and whether the vocabulary had any same-cell alternative. */
	val tailDeaths = java.util.TreeMap<String, Int>()
	var tailDeathsNoAlternative = 0
		private set

	/** Shorter candidates accepted although their score is worse: the extra collisions all began on the ground. */
	var groundedBumpAccepts = 0
		private set

	fun diagnosis(): String = buildString {
		append("tried=").append(spansTried)
		append(" unreachable=").append(spansUnreachable)
		append(" landed=").append(landed)
		append(" cells=").append(cellHits)
		append(" gainless=").append(gainless)
		append(" crossed=").append(crossingsFound)
		append(" resolved=").append(resolvedDecisions)
		append(" reshaped=").append(reshapedDecisions)
		append(" skippedAhead=").append(skippedAhead)
		append(" tails=").append(tailsSurvived)
		append(" fasterTails=").append(fasterTails)
		append(" finishFailed=").append(finishFailed)
		append(" finishSlower=").append(finishSlower)
		if (finishSlower > 0) append(" (gain=").append(finishGainLost).append(" extra=").append(finishExtraFrames).append(')')
		append(" finishCollisions=").append(finishCollisions)
		append(" groundedBumps=").append(groundedBumpAccepts)
		append(" alternates=").append(alternatesOffered)
		if (tailDeaths.isNotEmpty()) append(" tailDeaths=").append(tailDeaths).append("/noAlt=").append(tailDeathsNoAlternative)
		if (tailMisses.isNotEmpty()) append(" tailMisses=").append(tailMisses)
	}

	/** [budget] is a cumulative session ceiling, including terminal attempts, not a per-call allowance. */
	fun improve(solution: Solution, budget: Int): Solution? {
		if (rolloutsSpent >= budget) return null
		var current = solution
		var improved = false
		var round = 0
		while (round++ < MAX_ROUNDS && rolloutsSpent < budget) {
			current = onePass(current, budget) ?: break
			improved = true
			splices++
		}
		return if (improved) current else null
	}

	private class Crossing(val anchor: ValueAnchor, val rejoin: Int)

	/** The first complete candidate that improves both score and frame count. */
	private fun onePass(solution: Solution, budget: Int): Solution? {
		val chain = chainOf(solution.anchor)
		if (chain.size < 3) return null
		val graph = PlanGraph.of(solution.planSegments, chain.first().state) ?: return null

		for (start in graph.improvementDepartures(chain.lastIndex)) {
			if (rolloutsSpent >= budget) return null
			val from = chain[start]
			spansTried++
			if (!canReach(from)) {
				spansUnreachable++
				continue
			}
			for (crossing in crossings(from, chain, start, budget)) {
				crossingsFound++
				val tail = recertify(chain, crossing.rejoin + 1, crossing.anchor, budget) ?: continue
				tailsSurvived++
				val last = chain.last()
				val faster = tail.elapsed < last.elapsed
				if (faster) fasterTails++
				val candidate = finishFrom(tail, budget)
				if (candidate == null) {
					if (faster) finishFailed++
					continue
				}
				if (candidate.frames >= solution.frames) {
					if (faster) {
						finishSlower++
						finishGainLost += last.elapsed - tail.elapsed
						finishExtraFrames += candidate.frames - solution.frames
					}
					continue
				}
				if (candidate.score >= solution.score) {
					// A shorter tape that only grazes ground the spine avoided: a bump while
					// grounded costs nothing the certifier did not already replay. Mid-air
					// bumps stay refused (docs/decisions/improver.md, second dig).
					if (!TOLERATE_GROUNDED_BUMPS || candidate.airborneCollisionEvents > solution.airborneCollisionEvents) {
						finishCollisions++
						continue
					}
					groundedBumpAccepts++
				}
				alternatesOffered++
				return candidate
			}
		}
		return null
	}

	/**
	 * Rejoin candidates from [from], lazily: the search vocabulary in price order, each
	 * followed up to [MAX_SHORTCUT_DEPTH] deep while it has not yet landed on the spine. A
	 * geometric rejoin is not yet a shortcut; the caller validates it and can ask for another.
	 */
	private fun crossings(from: ValueAnchor, chain: List<ValueAnchor>, start: Int, budget: Int): Sequence<Crossing> = sequence {
		val minRejoin = start + 1
		for (decision in candidates(from)) {
			yieldAll(landings(from, decision, chain, minRejoin, MAX_SHORTCUT_DEPTH, budget))
		}
	}

	private fun candidates(anchor: ValueAnchor): List<TrajectoryDecision> =
		vocabulary.actions(anchor, temperature).take(BRANCHING).map { it.decision }

	/** Roll [decision] from [anchor]; yield every junction it rejoins, else continue with [follow] while depth remains. */
	private fun landings(
		anchor: ValueAnchor,
		decision: TrajectoryDecision,
		chain: List<ValueAnchor>,
		minRejoin: Int,
		depth: Int,
		budget: Int,
	): Sequence<Crossing> = sequence {
		if (depth <= 0 || rolloutsSpent >= budget) return@sequence
		val next = transition(anchor, decision, budget) ?: return@sequence
		landed++
		val matches = rejoinIndices(next, chain, minRejoin)
		for (index in matches) yield(Crossing(next, index))
		if (matches.isNotEmpty() || depth <= 1) return@sequence
		if (next.elapsed >= chain.last().elapsed) return@sequence
		for (continuation in candidates(next)) {
			if (rolloutsSpent >= budget) return@sequence
			yieldAll(landings(next, continuation, chain, minRejoin, depth - 1, budget))
		}
	}

	/** Junctions [next] rejoins -- the same grounded cell, reached earlier -- furthest gain first. */
	private fun rejoinIndices(next: ValueAnchor, chain: List<ValueAnchor>, minRejoin: Int): List<Int> {
		val matches = ArrayList<Int>()
		for (index in minRejoin..chain.lastIndex) {
			val junction = chain[index]
			if (junction.stance != next.stance) continue
			cellHits++
			if (junction.elapsed <= next.elapsed) {
				gainless++
				continue
			}
			if (junction.state.onGround == next.state.onGround) matches += index
		}
		matches.sortByDescending { chain[it].elapsed }
		return matches
	}

	/**
	 * Re-run the plan's remaining decisions from a body that arrived differently. A
	 * decision carries the launch it solved for the body it was proposed from, so from a
	 * displaced body it is stale: it may miss its pad, or land later than a fresh solution
	 * would. Every decision that launches is therefore re-solved as well as replayed (any
	 * vocabulary decision onto the same cell, cheapest first, up to [RESOLVE_ATTEMPTS]).
	 * A landing counts wherever it meets the spine again: on the decision's own cell or on
	 * any later junction's cell (a faster body walks off a lip into the next cell), and
	 * the landing furthest ahead of the spine's schedule is kept. Ground moves that replay
	 * onto their cell are only replayed.
	 */
	private fun recertify(chain: List<ValueAnchor>, from: Int, entry: ValueAnchor, budget: Int): ValueAnchor? {
		var anchor = entry
		var index = from
		while (index <= chain.lastIndex) {
			val original = chain[index].decision
			if (original == null) {
				index++
				continue
			}
			var best: ValueAnchor? = null
			var bestAt = index
			var bestGain = Int.MIN_VALUE

			// A landing on the spine at junction `at`, `gain` frames ahead of the spine's schedule there.
			fun consider(outcome: Outcome?, fresh: Boolean): Boolean {
				val landed = (outcome as? Outcome.Anchored)?.anchor
				if (landed == null) {
					val kind = when (outcome) {
						null -> "budget"
						is Outcome.Rejected -> outcome.diagnostic::class.simpleName ?: "rejected"
						is Outcome.Blocked -> "blocked"
						is Outcome.Arrived -> "arrived"
						is Outcome.Anchored -> "?"
					}
					tailMisses[kind] = (tailMisses[kind] ?: 0) + 1
					return false
				}
				val at = (index..chain.lastIndex).firstOrNull { chain[it].stance == landed.stance }
				if (at == null) {
					tailMisses["off-spine"] = (tailMisses["off-spine"] ?: 0) + 1
					return false
				}
				val gain = chain[at].elapsed - landed.elapsed
				if (gain > bestGain) {
					if (best != null && fresh) reshapedDecisions++
					if (best == null && fresh) resolvedDecisions++
					best = landed
					bestAt = at
					bestGain = gain
				}
				return true
			}

			val replayedOnto = consider(attempt(anchor, original, budget), fresh = false)
			val launches = original.launchDelayFrames != null || original.leavesGround ||
					original.movement != com.lambda.pathing.core.MovementId.WALK
			if (launches || !replayedOnto) {
				if (rolloutsSpent >= budget) return null
				val alternatives = vocabulary.actions(anchor, temperature).asSequence()
					.map { it.decision }
					.filter { it != original && it.step == original.step }
					.take(RESOLVE_ATTEMPTS)
					.toList()
				if (!replayedOnto && alternatives.isEmpty()) tailDeathsNoAlternative++
				for (alternative in alternatives) {
					if (rolloutsSpent >= budget) break
					consider(attempt(anchor, alternative, budget), fresh = true)
				}
			}
			anchor = best ?: run {
				val kind = original::class.simpleName ?: "?"
				tailDeaths[kind] = (tailDeaths[kind] ?: 0) + 1
				return null
			}
			if (bestAt > index) skippedAhead++
			index = bestAt + 1
		}
		return anchor
	}

	/** Every simulator attempt must acquire a unit before starting, even when it fails. */
	private fun trySpend(budget: Int): Boolean {
		if (rolloutsSpent >= budget) return false
		rolloutsSpent++
		return true
	}

	private fun transition(anchor: ValueAnchor, decision: TrajectoryDecision, budget: Int): ValueAnchor? =
		(attempt(anchor, decision, budget) as? Outcome.Anchored)?.anchor

	private fun attempt(anchor: ValueAnchor, decision: TrajectoryDecision, budget: Int): Outcome? {
		if (!trySpend(budget)) return null
		return rollouts.transition(anchor, decision, null)
	}

	private fun finishFrom(anchor: ValueAnchor, budget: Int): Solution? =
		finisher.finishFrom(anchor, canStartRollout = { trySpend(budget) }, exhaustive = true)

	private fun chainOf(leaf: ValueAnchor): List<ValueAnchor> {
		val chain = ArrayList<ValueAnchor>()
		var node: ValueAnchor? = leaf
		while (node != null) {
			chain += node
			node = node.parent
		}
		chain.reverse()
		return chain
	}

	private companion object {
		/** Movements tried per anchor while crossing a span with the vocabulary, cheapest first. */
		const val BRANCHING = 8

		/** A shortcut worth having is one or two movements; four is already a detour. */
		const val MAX_SHORTCUT_DEPTH = 3

		/** Fresh solutions of one edge tried when the spine's own decision fails from a displaced body. */
		const val RESOLVE_ATTEMPTS = 3

		const val MAX_ROUNDS = 8

		/** Whether a frame-shorter candidate may add grounded collision events. Experiment: see docs/decisions/improver.md. */
		const val TOLERATE_GROUNDED_BUMPS = true
	}
}
