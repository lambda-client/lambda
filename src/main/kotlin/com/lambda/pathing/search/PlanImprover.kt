package com.lambda.pathing.search

import com.lambda.pathing.actions.TerminalApproach
import com.lambda.pathing.actions.TrajectoryDecision

/**
 * Shortens a solution by crossing a costly spine span, re-running its remaining
 * decisions and finishing from the new body. Candidates are complete anchor chains:
 * accepting one requires no second route reconstruction or simulator pass.
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
    var tailsSurvived = 0
        private set
    var alternatesOffered = 0
        private set

    fun diagnosis(): String = buildString {
        append("tried=").append(spansTried)
        append(" unreachable=").append(spansUnreachable)
        append(" crossed=").append(crossingsFound)
        append(" tails=").append(tailsSurvived)
        append(" alternates=").append(alternatesOffered)
        if (nearestMissBlocks.isFinite()) append(" nearestMiss=%.2f".format(nearestMissBlocks))
    }

    /** [budget] is a cumulative session ceiling, including terminal attempts, not a per-call allowance. */
    fun improve(solution: Solution, budget: Int): Solution? {
        if (rolloutsSpent >= budget) return null
        var current = solution
        var improved = false

        var round = 0
        while (round++ < MAX_ROUNDS && rolloutsSpent < budget) {
            val next = onePass(current, budget) ?: break
            current = next
            improved = true
            splices++
        }
        return if (improved) current else null
    }

    private class Crossing(val anchor: ValueAnchor, val rejoin: Int)

    /**
     * The plan DAG's quality producer on a partial tape: rewrite one span of the published
     * spine between two junctions the body has not reached, re-run the remaining decisions
     * from the rejoin, and return the rewritten tip when it arrives at least [minGainFrames]
     * earlier (collisions priced as [Solution.score] prices them). No finisher is involved:
     * the tip's own brake is re-certified by the publication gate. Null when no span improves.
     */
    fun improveTip(tip: ValueAnchor, budget: Int, minGainFrames: Int = 1): ValueAnchor? {
        if (budget <= 0 || rolloutsSpent >= budget) return null
        val chain = chainOf(tip)
        if (chain.size < 3) return null
        val segments = Solution.segmentsOf(tip, emptyList(), TerminalApproach(sprint = false, lookAheadNodes = 1, brakeDistance = 0.0, stepUpJumpLeadDistance = null))
        val graph = PlanGraph.of(segments, chain.first().state) ?: return null
        val tipScore = tip.elapsed + Solution.COLLISION_FRAME_PENALTY * tip.collisionEvents

        for (start in graph.improvementDepartures(chain.lastIndex)) {
            if (rolloutsSpent >= budget) return null
            val from = chain[start]
            spansTried++
            if (!canReach(from)) {
                spansUnreachable++
                continue
            }
            for (crossing in crossings(from, chain, start + 1, MAX_SHORTCUT_DEPTH, budget)) {
                crossingsFound++
                val tail = recertify(chain, crossing.rejoin + 1, crossing.anchor, budget) ?: continue
                tailsSurvived++
                val score = tail.elapsed + Solution.COLLISION_FRAME_PENALTY * tail.collisionEvents
                if (score + minGainFrames > tipScore) continue
                alternatesOffered++
                return tail
            }
        }
        return null
    }

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

            // Rejoining at the very next anchor is allowed; it is where nearly all wins are.
            for (crossing in crossings(from, chain, start + 1, MAX_SHORTCUT_DEPTH, budget)) {
                crossingsFound++

                val tail = recertify(chain, crossing.rejoin + 1, crossing.anchor, budget) ?: continue
                tailsSurvived++
                val candidate = finishFrom(tail, budget) ?: continue
                if (candidate.score >= solution.score) continue

                alternatesOffered++
                // The complete candidate already contains the shared prefix and the new
                // goal-reaching tail. No segment-graph reconstruction is needed.
                if (candidate.frames >= solution.frames) continue
                return candidate
            }
        }
        return null
    }

    /**
     * Offer rejoins lazily in depth-first action order. A geometric rejoin is not yet
     * a viable shortcut: if its tail or finish fails, the caller can request another.
     * Rollouts stop immediately when a winner is accepted or the shared budget expires.
     */
    private fun crossings(
        anchor: ValueAnchor,
        chain: List<ValueAnchor>,
        minRejoin: Int,
        depth: Int,
        budget: Int,
    ): Sequence<Crossing> = sequence {
        if (depth <= 0 || rolloutsSpent >= budget) return@sequence
        val actions = vocabulary.actions(anchor, temperature)
        for (index in 0 until minOf(actions.size, BRANCHING)) {
            if (rolloutsSpent >= budget) return@sequence
            val next = transition(anchor, actions[index].decision, budget) ?: continue
            val rejoin = rejoinIndex(next, chain, minRejoin)
            if (rejoin != null) {
                yield(Crossing(next, rejoin))
                continue
            }
            if (next.elapsed >= chain.last().elapsed) continue
            yieldAll(crossings(next, chain, minRejoin, depth - 1, budget))
        }
    }

    /**
     * The furthest junction this crossing rejoins, matched by exact stance or by
     * [RejoinRule]; [recertify] validates every match.
     */
    private fun rejoinIndex(next: ValueAnchor, chain: List<ValueAnchor>, minRejoin: Int): Int? {
        var best: Int? = null
        var bestGain = 0
        for (index in minRejoin..chain.lastIndex) {
            val junction = chain[index]
            val gain = junction.elapsed - next.elapsed
            if (gain <= bestGain) continue
            if (junction.stance == next.stance || statesRejoin(next, junction)) {
                bestGain = gain
                best = index
            }
        }
        return best
    }

    /** How close crossings get to junctions they fail to match: the tolerance's report card. */
    var nearestMissBlocks = Double.POSITIVE_INFINITY
        private set

    private fun statesRejoin(candidate: ValueAnchor, junction: ValueAnchor): Boolean {
        if (candidate.state.onGround != junction.state.onGround) return false
        val matches = RejoinRule.rejoins(candidate.state, junction.state)
        if (!matches) {
            val positionError = RejoinRule.positionError(candidate.state, junction.state)
            if (positionError < nearestMissBlocks) nearestMissBlocks = positionError
        }
        return matches
    }

    /** Re-run the plan's remaining decisions from a body that arrived differently. */
    private fun recertify(chain: List<ValueAnchor>, from: Int, entry: ValueAnchor, budget: Int): ValueAnchor? {
        var anchor = entry
        for (index in from..chain.lastIndex) {
            val decision = chain[index].decision ?: continue
            anchor = transition(anchor, decision, budget) ?: return null
        }
        return anchor
    }

    /** Every simulator attempt must acquire a unit before starting, even when it fails. */
    private fun trySpend(budget: Int): Boolean {
        if (rolloutsSpent >= budget) return false
        rolloutsSpent++
        return true
    }

    private fun transition(anchor: ValueAnchor, decision: TrajectoryDecision, budget: Int): ValueAnchor? {
        if (!trySpend(budget)) return null
        return (rollouts.transition(anchor, decision, null) as? Outcome.Anchored)?.anchor
    }

    private fun finishFrom(anchor: ValueAnchor, budget: Int): Solution? =
        finisher.finishFrom(anchor, canStartRollout = { trySpend(budget) })

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
        /** Movements tried per anchor while crossing a span, cheapest first. */
        const val BRANCHING = 8

        /** A shortcut worth having is one or two movements; four is already a detour. */
        const val MAX_SHORTCUT_DEPTH = 3

        const val MAX_ROUNDS = 8
    }
}
