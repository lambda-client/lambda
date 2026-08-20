/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.util.player.prediction.MovementSimulationState

/**
 * What one stretch of a tape cost, against what the coarse layer says it should have.
 *
 * The coarse field prices every stance in ticks-to-go, so the drop in that price across a
 * stretch is what the stretch was *worth*: the ticks of progress it bought. The frames it
 * actually took is what it *cost*. The difference is time the trajectory layer lost — to a
 * turn it took wide, a jump it lined up for, a speed it had to shed.
 *
 * Some excess is unavoidable: the coarse price is a lower bound built from straight-line
 * moves between block centres, and a real body has to turn. What matters is not the
 * absolute number but where it concentrates, because that is the part of the path worth
 * attacking -- and until this existed there was no way to say which part that was, only
 * that the whole tape was longer than it should be.
 */
data class SegmentCost(
    /** Frame the stretch starts at, inclusive. */
    val fromFrame: Int,
    /** Frame the stretch ends at, exclusive. */
    val toFrame: Int,
    /** Frames actually spent here. */
    val actualTicks: Int,
    /** Ticks of coarse cost-to-go this stretch bought. */
    val boundTicks: Double,
    /**
     * The braking tail, whose excess is expected rather than a fault.
     *
     * The coarse layer prices *travel* between stances and says nothing about coming to a
     * stop, so the stretch that stops always looks like frames spent buying nothing. It is
     * flagged instead of dropped, because a terminal that has grown long is still worth
     * seeing -- it just should not be what the improver spends its budget attacking.
     */
    val terminal: Boolean = false,
) {
    /** Ticks spent beyond what the progress made here was worth. Never below zero. */
    val excessTicks: Double get() = (actualTicks - boundTicks).coerceAtLeast(0.0)

    /** Excess per frame; what makes a short bad stretch comparable to a long mediocre one. */
    val excessRate: Double get() = if (actualTicks <= 0) 0.0 else excessTicks / actualTicks
}

object SegmentCosts {
    /**
     * Attributes a tape's cost to its stretches, cut at the frames it can actually be cut
     * at.
     *
     * Boundaries are where one decision hands over to the next, because those are the only
     * frames anything may re-plan from -- attributing cost to a stretch nothing can
     * replace would be describing a problem with no available fix.
     */
    fun attribute(
        initial: MovementSimulationState,
        frames: List<SimulatedTrajectoryFrame>,
        boundaries: List<Int>,
        field: CoarseValueField,
    ): List<SegmentCost> {
        if (frames.isEmpty()) return emptyList()
        val cuts = (listOf(0) + boundaries.filter { it in 1..frames.size } + listOf(frames.size))
            .distinct()
            .sorted()
        if (cuts.size < 2) return emptyList()

        fun valueAt(frame: Int): Double {
            val state = if (frame == 0) initial else frames[frame - 1].state
            return field.guide(ValueFieldAnchorSearch.stanceOf(state))
        }

        val costs = ArrayList<SegmentCost>(cuts.size - 1)
        for (index in 0 until cuts.lastIndex) {
            val from = cuts[index]
            val to = cuts[index + 1]
            val before = valueAt(from)
            val after = valueAt(to)
            // An unpriced end makes the stretch unattributable rather than free: claiming
            // zero progress would blame it for every frame it spent.
            if (!before.isFinite() || !after.isFinite()) continue
            costs += SegmentCost(from, to, to - from, before - after, terminal = to >= frames.size)
        }
        return costs
    }
}
