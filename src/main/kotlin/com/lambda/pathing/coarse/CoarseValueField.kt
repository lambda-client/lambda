/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.coarse

import com.lambda.pathing.world.CoarseVoxelView
import kotlin.math.floor
import kotlin.math.hypot

/**
 * The coarse layer's cost-to-go as a *field* over stances rather than one extracted path.
 *
 * D* Lite searches backwards from the goal, so every stance it touched already carries a
 * cost-to-go label; the greedy route is just one chain read out of it. Publishing the
 * field instead lets the trajectory search steer by value from wherever the body actually
 * is — including stances no route ever named, which is the whole point: a jump the coarse
 * move library cannot express still lands somewhere the field can price.
 *
 * Worker-local by construction. It closes over the frozen snapshot view and the D* labels,
 * so it must never cross the publication boundary (see [CoarseRoutePlan], which is the
 * thing that may).
 */
class CoarseValueField(
    private val view: CoarseVoxelView,
    private val moves: SimpleMoveLibrary,
    /** `min(g, rhs)` from D*: infinite for a stance the backward search never touched. */
    private val label: (Stance) -> Double,
    val goal: Stance,
) {
    private val guides = HashMap<Stance, Double>()
    private val edges = HashMap<Stance, List<CoarseEdge>>()

    /** Stances whose value had to be guessed; a large share means the field is too thin. */
    var unlabelledQueries = 0
        private set

    /** Admissible ticks-to-go. Never uses a D* label, so it bounds every completion. */
    fun lowerBound(stance: Stance): Double = moves.heuristic(stance, goal)

    /**
     * Ticks-to-go for ordering: the D* label where the backward search reached this
     * stance, a one-step lookahead through a labelled neighbour where it did not, and
     * **infinity** otherwise.
     *
     * Infinity, not a penalised heuristic. A straight-line heuristic cannot see the walls
     * and gaps the labels already price in, so on broken terrain it under-prices unmapped
     * ground by *tens* of ticks — far more than any fixed penalty absorbs — and the search
     * is then actively attracted to exactly the ground nobody has verified is traversable.
     * That is a search walking off in the wrong direction, and it was observed in the
     * field. Where there is no map, do not steer.
     *
     * The map is made wide enough to steer inside by
     * [com.lambda.pathing.core.DStarLite.expandField], which is the supported way to buy
     * manoeuvring room — not by guessing values.
     */
    fun guide(stance: Stance): Double = guides.getOrPut(stance) {
        val direct = label(stance)
        if (direct.isFinite()) return@getOrPut direct

        var best = Double.POSITIVE_INFINITY
        for (edge in edgesFrom(stance)) {
            val neighbour = label(edge.to)
            if (neighbour.isFinite()) best = minOf(best, edge.lowerBoundTicks + neighbour)
        }
        if (!best.isFinite()) unlabelledQueries++
        best
    }

    /** Whether the field can price this stance at all; unmapped ground is not steerable. */
    fun isMapped(stance: Stance): Boolean = guide(stance).isFinite()

    fun edgesFrom(stance: Stance): List<CoarseEdge> = edges.getOrPut(stance) { moves.edgesFrom(view, stance) }

    fun isStance(stance: Stance): Boolean = moves.isStance(view, stance)

    /**
     * The [count] most promising coarse steps out of [stance], cheapest `edge + value`
     * first, one per destination.
     *
     * This is the replacement for "the route says go here": a *set* of directions the
     * trajectory search may commit to, not a single one chosen before any physics ran.
     */
    fun steps(
        stance: Stance,
        count: Int,
        marginTicks: Double = Double.MAX_VALUE,
        heading: Pair<Double, Double>? = null,
    ): List<CoarseEdge> {
        val byDestination = HashMap<Stance, CoarseEdge>()
        for (edge in edgesFrom(stance)) {
            val incumbent = byDestination[edge.to]
            if (incumbent == null || edge.lowerBoundTicks < incumbent.lowerBoundTicks) {
                byDestination[edge.to] = edge
            }
        }
        // Rank in tie bands, not on raw value. A lattice offers many equal-value
        // realisations of the same diagonal line, and picking between them by raw value
        // alone lets the choice flip from one anchor to the next — a heading change every
        // block, which is a measurably longer and slower line even though every single
        // step was "optimal". Within a band the body's own momentum decides.
        byDestination.values.retainAll { isMapped(it.to) }
        val reference = reference(stance, heading)
        val ranked = byDestination.values.sortedWith(
            compareBy<CoarseEdge> { floor((it.lowerBoundTicks + guide(it.to)) / TIE_TICKS) }
                .thenByDescending { alignment(stance, it.to, reference) }
                .thenBy { it.to.y }.thenBy { it.to.x }.thenBy { it.to.z }
        )
        val best = ranked.minOfOrNull { it.lowerBoundTicks + guide(it.to) } ?: return emptyList()
        // Branch only where the field says the choice is genuinely close. A direction the
        // field prices ten ticks worse is not an alternative the body should be simulated
        // into; it is the wrong way. Everywhere else the freedom costs simulations and
        // buys nothing — which is exactly what the first corpus run measured.
        return ranked.takeWhile { it.lowerBoundTicks + guide(it.to) <= best + marginTicks }.take(count)
    }

    /**
     * A short polyline for the controller to steer at, descending the field from [stance].
     *
     * Regenerated at every anchor instead of committed to once, so it is a *lookahead*,
     * not a plan: the body is free to end up somewhere the previous chain never mentioned,
     * and the next chain is simply drawn from there.
     */
    fun chain(
        stance: Stance,
        firstStep: Stance? = null,
        length: Int,
        heading: Pair<Double, Double>? = null,
    ): List<Stance> {
        require(length > 0) { "A steering chain needs at least one node" }
        val chain = ArrayList<Stance>(length + 1)
        chain += stance
        val visited = HashSet<Stance>()
        visited += stance

        var direction = heading
        firstStep?.let {
            direction = (it.x - stance.x).toDouble() to (it.z - stance.z).toDouble()
            chain += it
            visited += it
        }

        while (chain.size <= length && chain.last() != goal) {
            val from = chain.last()
            val next = descend(from, visited, direction) ?: break
            direction = (next.x - from.x).toDouble() to (next.z - from.z).toDouble()
            chain += next
            visited += next
        }
        return chain
    }

    /**
     * One step down the field, breaking ties toward the current heading.
     *
     * On a block lattice a diagonal line has *many* equal-cost realisations, so a plain
     * value descent staircases between them: the controller is then handed a jagged
     * polyline and wobbles along a line that should be straight. The field is indifferent
     * between those steps; the body is not, because turning sheds momentum. D*'s own route
     * extraction takes a deterministic tie-break for the same reason — its comment says
     * tie instability "cascades into refinement" — so a field readout must too, and the
     * physically meaningful order to impose is "keep going the way you are going".
     */
    private fun descend(
        from: Stance,
        visited: Set<Stance>,
        heading: Pair<Double, Double>?,
    ): Stance? {
        val candidates = edgesFrom(from).filter { it.to !in visited && guide(it.to).isFinite() }
        if (candidates.isEmpty()) return null
        val best = candidates.minOf { it.lowerBoundTicks + guide(it.to) }
        val tied = candidates.filter { it.lowerBoundTicks + guide(it.to) <= best + TIE_TICKS }
        val reference = reference(from, heading)
        return tied.minWithOrNull(
            compareByDescending<CoarseEdge> { alignment(from, it.to, reference) }
                .thenBy { it.to.y }.thenBy { it.to.x }.thenBy { it.to.z }
        )?.to
    }

    /**
     * The direction ties are resolved toward: the body's own momentum, or — when it has
     * none — the line to the goal.
     *
     * The fallback is the whole point. On open ground many steps tie in value, and with no
     * momentum to break the tie the order fell through to lattice order `(y, x, z)`, which
     * simply picks the lowest coordinate: a body starting from rest on a straight route
     * turned 48 degrees off the line on its first tick and spent the rest of the tape
     * correcting with full-rate swings. Ties must resolve toward *somewhere*, and the goal
     * is the one direction that is always meaningful.
     */
    private fun reference(from: Stance, heading: Pair<Double, Double>?): Pair<Double, Double> {
        if (heading != null && hypot(heading.first, heading.second) > 1e-6) return heading
        return Pair((goal.x - from.x).toDouble(), (goal.z - from.z).toDouble())
    }

    /** Cosine of the turn this step asks for; 1.0 is straight on, -1.0 is a reversal. */
    private fun alignment(from: Stance, to: Stance, heading: Pair<Double, Double>?): Double {
        val (hx, hz) = heading ?: return 0.0
        val headingLength = hypot(hx, hz)
        if (headingLength <= 1e-9) return 0.0
        val dx = (to.x - from.x).toDouble()
        val dz = (to.z - from.z).toDouble()
        val stepLength = hypot(dx, dz)
        if (stepLength <= 1e-9) return 0.0
        return (hx * dx + hz * dz) / (headingLength * stepLength)
    }

    /** Whether [chain] actually terminates at the goal, so a braking tail may be planned on it. */
    fun reachesGoal(chain: List<Stance>): Boolean = chain.lastOrNull() == goal

    private companion object {
        /** Value difference below which two steps are the same choice to the field. */
        const val TIE_TICKS = 0.5
    }
}
