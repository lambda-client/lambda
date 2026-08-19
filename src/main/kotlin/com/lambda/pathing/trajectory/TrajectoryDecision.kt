/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.Stance

/**
 * One committed choice in a trajectory: what the search decided to *do*, as opposed to
 * the keys that decision happened to produce.
 *
 * A certified plan carries both. The inputs are what the executor replays, tick by tick,
 * and they are the thing the live body is verified against. The decisions are what makes
 * a plan *editable*: they can be re-run from a different state.
 *
 * That distinction is load-bearing. Splicing a faster middle into a tape cannot reproduce
 * the old join state exactly — no input sequence hits a specific continuous state — and a
 * raw input tail replayed from even a quarter-block error fails outright (measured: 0 of 7
 * surviving). Re-running the *decisions* from the same perturbed state survived 7 of 7,
 * because these controllers steer at world targets and fire launches on grounded ticks
 * rather than fixed frame indices: a body arriving slightly wide steers back, and one
 * arriving a tick late still jumps on landing instead of in mid-air.
 */
sealed interface TrajectoryDecision {
    /** Whether the sprint key is held; vanilla only sustains it while forward is held. */
    val sprint: Boolean

    /** The coarse step this decision was aimed at, for re-deriving the steering line. */
    val step: Stance?

    /** Pure pursuit toward a stance centre. */
    data class Walk(
        override val sprint: Boolean,
        override val step: Stance?,
        val lookAheadNodes: Int,
        val easeTurns: Boolean,
    ) : TrajectoryDecision

    /** A launch on the [delayFrames]-th grounded tick, aimed along the coarse step. */
    data class Launch(
        override val sprint: Boolean,
        override val step: Stance?,
        val delayFrames: Int,
    ) : TrajectoryDecision

    /**
     * Hold a world heading, optionally jumping partway through. Off-lattice: where the
     * body ends up is decided by this heading and its own momentum, not by a block centre.
     */
    data class Heading(
        override val sprint: Boolean,
        override val step: Stance?,
        val yaw: Double,
        val offsetDegrees: Double,
        val delayFrames: Int?,
        val keys: MovementKeys = MovementKeys.FORWARD,
        val airborneKeys: MovementKeys = keys,
    ) : TrajectoryDecision
}

/**
 * The decisions behind a certified tape, in order, plus the braking parameters that ended
 * it. Enough to reproduce the plan's intent from any nearby state.
 */
data class TrajectoryPlanDecisions(
    val decisions: List<TrajectoryDecision>,
    /**
     * Where each decision was originally simulated to end.
     *
     * A re-run needs this to stay faithful. Repairing a launch by whichever timing makes
     * the most immediate progress measured *worse* than not repairing at all: it lands
     * somewhere the plan never planned for, and every later decision was chosen for the
     * line it just left. Matching the intended state keeps the repair a repair.
     */
    val intended: List<com.lambda.util.player.prediction.MovementSimulationState> = emptyList(),
    /**
     * Frame each decision ends on, in tape coordinates.
     *
     * What makes a *tail* addressable. A splice replaces the tape up to some frame and
     * re-runs the rest, so it has to know which decision that frame lands inside; without
     * this the decision list can only ever be replayed whole, which is the one thing a
     * shortcut never wants to do.
     */
    val boundaries: List<Int> = emptyList(),
    /** Parameters of the terminal approach, or null when the tape ends in a plain brake. */
    val terminal: WalkingSeedParameters? = null,
) {
    /**
     * The decisions still outstanding at [frame], and where they were meant to lead.
     *
     * Returns null when the frame falls inside a decision rather than on its boundary:
     * re-entering halfway through a committed manoeuvre means re-running it from a state
     * it never started from, which is not the same plan.
     */
    fun suffixFrom(frame: Int): TrajectoryPlanDecisions? {
        if (boundaries.size != decisions.size) return null
        val index = boundaries.indexOfFirst { it >= frame }
        if (index < 0) return null
        if (boundaries[index] != frame) return null
        return TrajectoryPlanDecisions(
            decisions = decisions.drop(index + 1),
            intended = intended.drop(index + 1),
            boundaries = boundaries.drop(index + 1).map { it - frame },
            terminal = terminal,
        )
    }
}
