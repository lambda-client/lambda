package com.lambda.pathing.search

import java.util.IdentityHashMap

/**
 * Shortens a solved plan by offering [PlanGraph.Alternate]s: build the plan's graph, depart
 * its dearest span in fewer frames, re-run the rejoined decisions from where the shortcut
 * came out, and hand the graph's [PlanGraph.bestRoute] back as an ordinary [Solution] --
 * a real anchor chain, assembled from the anchors those segments were certified on.
 * See docs/decisions/improver.md.
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

    /**
     * Segments this pass certified, by identity, and the solutions whose terminal tails
     * they are: assembling a route through them costs no rollouts.
     */
    private class Certified {
        val anchors = IdentityHashMap<PlanSegment, ValueAnchor>()
        val finished = IdentityHashMap<PlanSegment, Solution>()

        /** Spine segment `i` is the run that produced `chain[i + 1]`; the tail has no anchor. */
        fun record(solution: Solution, chain: List<ValueAnchor>) {
            val segments = solution.planSegments
            for (index in 0 until minOf(segments.size, chain.size - 1)) anchors[segments[index]] = chain[index + 1]
            segments.lastOrNull()?.let { finished[it] = solution }
        }
    }

    /**
     * One improvement: the plan as a graph, its spans dearest first, the first crossing
     * whose re-run tail beats the incumbent offered as an alternate, and the graph's best
     * route assembled back into anchors. Junction `k` is `chain[k]` for every anchor; the
     * last junction may be the terminal tail's end, which nothing departs from.
     */
    private fun onePass(solution: Solution, budget: Int): Solution? {
        val chain = chainOf(solution.anchor)
        if (chain.size < 3) return null
        var graph = PlanGraph.of(solution.planSegments, chain.first().state) ?: return null
        val certified = Certified().apply { record(solution, chain) }

        for (start in departures(graph, chain)) {
            if (rolloutsSpent >= budget) return null
            val from = chain[start]
            spansTried++
            if (!canReach(from)) {
                spansUnreachable++
                continue
            }

            // Rejoining at the very next anchor is allowed; it is where nearly all wins are.
            val crossing = cross(from, chain, start + 1, MAX_SHORTCUT_DEPTH) ?: continue
            crossingsFound++

            val tail = recertify(chain, crossing.rejoin + 1, crossing.anchor) ?: continue
            tailsSurvived++
            val candidate = finisher.finishFrom(tail) ?: continue
            if (candidate.score >= solution.score) continue

            // The candidate shares the spine up to `start`; everything after is the alternate.
            val candidateChain = chainOf(candidate.anchor)
            certified.record(candidate, candidateChain)
            val segments = candidate.planSegments
            graph = graph.with(PlanGraph.Alternate(start, graph.junctions.lastIndex, segments.subList(start, segments.size)))
            alternatesOffered++

            // The graph prices frames; a win bought on collisions alone is offered but not routed.
            val route = graph.bestRoute()
            if (route.sumOf { it.frameCount } >= graph.frames) continue
            return assemble(route, chain.first(), certified) ?: continue
        }
        return null
    }

    /** Anchors to depart from, worst span ahead of them first; the ranking lives in the graph. */
    private fun departures(graph: PlanGraph, chain: List<ValueAnchor>): List<Int> {
        val seen = HashSet<Int>()
        val starts = ArrayList<Int>()
        for (span in graph.improvementTargets(PlanGraph.DEFAULT_MAX_SPAN)) {
            if (span.from >= chain.lastIndex) continue
            if (seen.add(span.from)) starts += span.from
        }
        return starts
    }

    /**
     * A route as an anchor chain: certified segments keep their anchors, anything else is
     * re-run from the anchor before it, and the terminal is the solution it came from or a
     * fresh finish. A brake tail in the middle of a route cannot be re-derived.
     */
    private fun assemble(route: List<PlanSegment>, root: ValueAnchor, certified: Certified): Solution? {
        var anchor = root
        for (segment in route) {
            val known = certified.anchors[segment]
            if (known != null && known.parent === anchor) {
                anchor = known
                continue
            }
            when (segment) {
                is PlanSegment.Move -> {
                    rolloutsSpent++
                    anchor = (rollouts.transition(anchor, segment.decision, null) as? Outcome.Anchored)
                        ?.anchor ?: return null
                }
                is PlanSegment.Terminal -> {
                    if (segment !== route.last()) return null
                    return certified.finished[segment]?.takeIf { it.anchor === anchor }
                        ?: finisher.finishFrom(anchor)
                }
            }
        }
        return finisher.finishFrom(anchor)
    }

    /**
     * Reach a rejoin from [anchor] within [depth] movements, cheapest options first.
     * Depth-limited rather than best-first: a shortcut worth having is one or two movements.
     */
    private fun cross(
        anchor: ValueAnchor,
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
            cross(next, chain, minRejoin, depth - 1)?.let { return it }
        }
        return null
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

        const val MAX_ROUNDS = 8

        /** A backstop so a pathological span cannot outrun the caller's budget check. */
        const val HARD_ROLLOUT_CAP = 100_000
    }
}
