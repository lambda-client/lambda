package com.lambda.pathing.trajectory

import com.lambda.pathing.core.Stance
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Makes a solved plan shorter by replacing expensive spans with shortcuts.
 *
 * This is the half of the design the search could never do. The old search had one
 * frontier answering "do we have a path" and "is it a good path" at once, which is why it
 * could be a hundred thousand expansions deep and still have nothing to hand the body.
 * By the time this runs a full solution already exists, so every rollout spent here can
 * only buy a shorter tape -- and if it buys nothing, the solution is still there.
 *
 * The loop is: rank spans of the solution's anchor chain by frames per block covered, try
 * to cross the worst one in fewer frames, then **re-run the rest of the plan's decisions
 * from wherever the shortcut actually came out**. That last step is why decisions are
 * retained at all. Replaying the recorded inputs from a displaced body re-certifies 0.9%
 * of the time at one beam bucket; re-running the decisions re-certifies 45%, because a
 * movement program re-solves its launch instead of replaying a stale one.
 *
 * Everything is rebuilt as a real anchor chain rather than assembled by hand, so the
 * result is an ordinary [Solution] -- certification, publication and the executor see
 * nothing new. A refused shortcut costs a median two to six rollouts, against the
 * seven-hundred-odd per published frame the receding-horizon search was measured burning.
 * Failure is cheap here on purpose.
 */
internal class PlanImprover(
    private val rollouts: AnchorRollout,
    private val vocabulary: ActionSet,
    private val finisher: FinishPlanner,
    /**
     * Whether a cut point is still adoptable.
     *
     * A splice behind the executed cursor can never be published, and offering one is
     * worse than useless: it spends the budget and then loses to the tape the body is
     * already replaying.
     */
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

        // Any anchor further along the plan is a legal rejoin, not just a chosen one.
        // Demanding an exact landing on a pre-selected junction found nothing at all:
        // the vocabulary proposes coarse steps within three of the body, and a junction
        // two to eight segments ahead is simply not something it aims at. Offering the
        // whole remaining chain as targets costs nothing and is what makes this fire.
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

            // Rejoining at the very next anchor is allowed, and turns out to be where
            // nearly all the wins are. Skipping a segment needs a coarse move longer than
            // the one the plan used, and the move library rarely has one -- measured as
            // one crossing across seventeen fully-reachable spans. Reaching the SAME
            // stance in fewer frames only needs a better-flown version of the same
            // movement, which the vocabulary offers several of.
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
            // Ranking executed spans burned most of a live slice's budget on cut points
            // that can never publish -- 29 of 35 span attempts on a walking traverse.
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
     * Reach [target] from [anchor] before frame [limit], cheapest options first.
     *
     * Depth-limited rather than best-first: a shortcut worth having is one or two
     * movements, and the frame limit prunes hard enough that a wider search buys nothing.
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
     * The junction this crossing rejoins, by body state rather than cell identity.
     *
     * Demanding the exact coarse stance the plan visits was the improver's cage: a
     * maneuver that carries speed lands wherever momentum puts it, usually a cell the
     * plan never touches, and the old test threw every such crossing away -- measured
     * as ~0 splices per field walk from 1,500 rollouts. What actually decides whether
     * the tail survives is the body state at the junction, and that tolerance is
     * measured, not guessed: decision re-runs re-certify 77% from a quarter beam
     * bucket, 56% from half. Half a bucket is accepted here because [recertify]
     * validates every candidate anyway -- a false accept costs its rollouts, a false
     * reject costs the splice. The exact-stance rejoin stays as one clause of the
     * match; it needs no state agreement because the re-run has always been trusted to
     * settle those.
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

        /** From the measured re-certification depth: past this the tail rarely survives. */
        const val MAX_SPAN = 8

        const val MIN_SPAN_BLOCKS = 1.0

        /** One beam position bucket: decision re-runs still re-certify 45% from here, and recertify filters the rest. */
        const val REJOIN_POSITION_TOLERANCE_BLOCKS = 0.25

        /** One beam speed bucket, same experiment. */
        const val REJOIN_VELOCITY_TOLERANCE_BLOCKS = 0.075
        const val MAX_ROUNDS = 8

        /** A backstop so a pathological span cannot outrun the caller's budget check. */
        const val HARD_ROLLOUT_CAP = 100_000
    }
}
