package com.lambda.pathing.trajectory

import com.lambda.pathing.core.Stance
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Shortens a solved plan by replacing expensive spans with shortcuts: rank spans of the
 * anchor chain by frames per block, cross the worst in fewer frames, then re-run the rest
 * of the plan's decisions from where the shortcut came out. The result is an ordinary
 * [Solution] built as a real anchor chain. See docs/decisions/improver.md.
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

    fun diagnosis(): String = buildString {
        append("tried=").append(spansTried)
        append(" unreachable=").append(spansUnreachable)
        append(" crossed=").append(crossingsFound)
        append(" tails=").append(tailsSurvived)
        if (nearestMissBlocks.isFinite()) append(" nearestMiss=%.2f".format(nearestMissBlocks))
    }

    /** The best solution found within [budget] rollouts, or null when nothing improved. */
    fun improve(solution: Solution, budget: Int): Solution? {
        if (budget <= 0) return null
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

    private fun onePass(solution: Solution, budget: Int): Solution? {
        val chain = chainOf(solution.anchor)
        if (chain.size < 3) return null

        // Any anchor further along the plan is a legal rejoin, not just a chosen junction.
        val rejoins = HashMap<Stance, Int>()
        chain.forEachIndexed { index, anchor -> rejoins[anchor.stance] = index }

        for (start in rankedStarts(chain)) {
            if (rolloutsSpent >= budget) return null
            val from = chain[start]
            spansTried++
            if (!canReach(from)) {
                spansUnreachable++
                continue
            }

            // Rejoining at the very next anchor is allowed; it is where nearly all wins are.
            val crossing = cross(from, rejoins, chain, start + 1, MAX_SHORTCUT_DEPTH) ?: continue
            crossingsFound++

            val tail = recertify(chain, crossing.rejoin + 1, crossing.anchor) ?: continue
            tailsSurvived++
            val candidate = finisher.finishFrom(tail) ?: continue
            if (candidate.score < solution.score) return candidate
        }
        return null
    }

    /** Live anchors to depart from, worst stretch ahead of them first. */
    private fun rankedStarts(chain: List<ValueAnchor>): List<Int> {
        val worst = HashMap<Int, Double>()
        for (start in chain.indices) {
            // Executed spans can never publish; do not spend budget ranking them.
            if (!canReach(chain[start])) continue
            for (end in start + 2..minOf(start + MAX_SPAN, chain.lastIndex)) {
                val frames = chain[end].elapsed - chain[start].elapsed
                if (frames <= 0) continue
                val blocks = chain[start].state.position.distanceTo(chain[end].state.position)
                if (blocks < MIN_SPAN_BLOCKS) continue
                val cost = frames / blocks
                if (cost > (worst[start] ?: 0.0)) worst[start] = cost
            }
        }
        return worst.entries.sortedByDescending { it.value }.map { it.key }
    }

    /**
     * Reach a rejoin from [anchor] within [depth] movements, cheapest options first.
     * Depth-limited rather than best-first: a shortcut worth having is one or two movements.
     */
    private fun cross(
        anchor: ValueAnchor,
        rejoins: Map<Stance, Int>,
        chain: List<ValueAnchor>,
        minRejoin: Int,
        depth: Int,
    ): Crossing? {
        if (depth <= 0) return null
        for (priced in vocabulary.actions(anchor, temperature).take(BRANCHING)) {
            if (rolloutsSpent >= HARD_ROLLOUT_CAP) return null
            rolloutsSpent++
            val next = (rollouts.transition(anchor, priced.decision, null) as? Outcome.Anchored)
                ?.anchor ?: continue
            val rejoin = rejoinIndex(next, chain, minRejoin)
            if (rejoin != null) return Crossing(next, rejoin)
            if (next.elapsed >= chain.last().elapsed) continue
            cross(next, rejoins, chain, minRejoin, depth - 1)?.let { return it }
        }
        return null
    }

    /**
     * The furthest junction this crossing rejoins, matched by exact stance or by body
     * state within the rejoin tolerances; [recertify] validates every match.
     * See docs/decisions/improver.md.
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
        val a = candidate.state
        val b = junction.state
        if (a.onGround != b.onGround) return false
        val positionError = maxOf(
            abs(a.position.y - b.position.y),
            hypot(a.position.x - b.position.x, a.position.z - b.position.z),
        )
        val velocityError = hypot(a.velocity.x - b.velocity.x, a.velocity.z - b.velocity.z)
        val matches = positionError <= REJOIN_POSITION_TOLERANCE_BLOCKS &&
            velocityError <= REJOIN_VELOCITY_TOLERANCE_BLOCKS
        if (!matches && positionError < nearestMissBlocks) nearestMissBlocks = positionError
        return matches
    }

    /** Re-run the plan's remaining decisions from a body that arrived differently. */
    private fun recertify(chain: List<ValueAnchor>, from: Int, entry: ValueAnchor): ValueAnchor? {
        var anchor = entry
        for (index in from..chain.lastIndex) {
            val decision = chain[index].decision ?: continue
            rolloutsSpent++
            anchor = (rollouts.transition(anchor, decision, null) as? Outcome.Anchored)
                ?.anchor ?: return null
        }
        return anchor
    }

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

        /** Segments a shortcut may span: the re-certification depth. See docs/decisions/improver.md. */
        const val MAX_SPAN = 8

        const val MIN_SPAN_BLOCKS = 1.0

        /** Rejoin tolerances: one beam position bucket and one beam speed bucket. */
        const val REJOIN_POSITION_TOLERANCE_BLOCKS = 0.25

        const val REJOIN_VELOCITY_TOLERANCE_BLOCKS = 0.075
        const val MAX_ROUNDS = 8

        /** A backstop so a pathological span cannot outrun the caller's budget check. */
        const val HARD_ROLLOUT_CAP = 100_000
    }
}
