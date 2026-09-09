package com.lambda.pathing.search

import com.lambda.pathing.actions.TrajectoryDecision

internal class PlanImprover(
	private val rollouts: AnchorRollout,
	private val vocabulary: ActionSet,
	private val finisher: FinishPlanner,

	private val canReach: (ValueAnchor) -> Boolean = { true },
	private val temperature: Temperature = Temperature(level = 1.0),
) {
	var rolloutsSpent = 0
		private set
	var splices = 0
		private set

	var spansTried = 0
		private set
	var spansUnreachable = 0
		private set
	var crossingsFound = 0
		private set

	var landed = 0
		private set
	var cellHits = 0
		private set
	var gainless = 0
		private set
	var tailsSurvived = 0
		private set

	var resolvedDecisions = 0
		private set

	var reshapedDecisions = 0
		private set

	val tailMisses = java.util.TreeMap<String, Int>()

	var skippedAhead = 0
		private set
	var alternatesOffered = 0
		private set

	var fasterTails = 0
		private set
	var finishFailed = 0
		private set
	var finishSlower = 0
		private set

	var finishGainLost = 0
		private set
	var finishExtraFrames = 0
		private set
	var finishCollisions = 0
		private set

	val tailDeaths = java.util.TreeMap<String, Int>()
	var tailDeathsNoAlternative = 0
		private set

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

					if (candidate.airborneCollisionEvents > solution.airborneCollisionEvents) {
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

	private fun crossings(from: ValueAnchor, chain: List<ValueAnchor>, start: Int, budget: Int): Sequence<Crossing> = sequence {
		val minRejoin = start + 1
		for (decision in candidates(from)) {
			yieldAll(landings(from, decision, chain, minRejoin, MAX_SHORTCUT_DEPTH, budget))
		}
	}

	private fun candidates(anchor: ValueAnchor): List<TrajectoryDecision> =
		vocabulary.actions(anchor, temperature).take(BRANCHING).map { it.decision }

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

		const val BRANCHING = 8

		const val MAX_SHORTCUT_DEPTH = 3

		const val RESOLVE_ATTEMPTS = 3

		const val MAX_ROUNDS = 8

	}
}
