/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.config.blocks

/**
 * What the coarse planner and the walking seed search are allowed to do.
 *
 * This rides on [com.lambda.context.Automated], so a module that requests a walk
 * plans under its own settings, exactly like its rotation config.
 */
interface PathingConfig {
    /** Allow 45-degree coarse edges. */
    val allowDiagonal: Boolean

    /** Allow one-block rises. They are jumped, not walked: vanilla step height is 0.6. */
    val allowStepUp: Boolean

    /** Let the seed search try sprinting gaits. */
    val allowSprint: Boolean

    /**
     * Deepest drop the coarse layer may propose.
     *
     * The graph knows only that the drop is clear, not that it is survivable, so the
     * trajectory layer certifies the landing and refuses harmful ones. Raising this
     * past the safe fall distance does not make the planner jump off cliffs; it makes
     * it *try*, and be refused.
     */
    val maxWalkOffDepth: Int

    /** Furthest a controlled drop may carry the body sideways while descending. */
    val maxDropSpan: Int get() = 2

    /**
     * Let the graph route up and down ladders and vines.
     *
     * Off by default, and the reason is not caution about the physics. Climb templates are
     * cheap per block, so registering them lowers the admissible ascent bound for *every*
     * search, including ones with no ladder within a hundred blocks. A weaker bound is a
     * slower search, and on a long route a slower search is one that runs out of expansions
     * before it arrives.
     */
    val allowClimbing: Boolean get() = false

    /**
     * Let the coarse layer propose gap jumps.
     *
     * These are *candidates* only: the graph masks them permissively from geometry,
     * and the trajectory layer must find a launch tick that actually lands before any
     * of them is executed. A candidate with no certificate is never replayed.
     */
    val allowJumpCandidates: Boolean

    /** Furthest a candidate jump may reach, in blocks. A span of 2 crosses one intermediate cell. */
    val maxJumpSpan: Int

    /** Deepest lower landing a jump candidate may target. */
    val maxJumpDrop: Int

    /**
     * Whether jumps that land off the eight compass rays are offered.
     *
     * Three across and one to the side is an unremarkable gap in anything built by hand, and
     * it has no cardinal or diagonal template. Roughly doubles the graph's fan-out on open
     * ground, which is the only reason it is a switch.
     */
    val allowOffAxisJumps: Boolean

    /**
     * Whether descending jumps are offered beyond the flat standing reach.
     *
     * Rollout-measured: half a block of real drop clears a four-cell air gap, a full
     * block the wide diagonals. Off by default because the wider fan measurably costs
     * search health on ordinary terrain; parkour courses with deep drop-gaps are what
     * it is for.
     */
    val allowDeepDropJumps: Boolean

    /**
     * Whether falls onto slime are offered as a way across.
     *
     * A bounce reaches ground nothing else does -- the fall supplies an impulse no jump key
     * can, and the rebound hands most of the height back. The arcs are thirty-odd ticks long
     * though, so each one the search tries is expensive, and the terrain that rewards them is
     * rare. Worth having where it exists, not worth paying for everywhere.
     */
    val allowSlimeBounces: Boolean

    /** Deepest fall onto slime a bounce may be planned around. */
    val maxBounceDrop: Int

    /**
     * Radius of the planning horizon around the body, in chunks; 0 plans everything at once.
     *
     * At full render distance a single converged coarse field can cost twenty seconds
     * before the first step. Inside a horizon the planner treats terrain past the ring
     * exactly as it treats terrain the server has not streamed: it routes to the ring's
     * edge on an optimistic edge, starts walking within a second or two, and each
     * continuation grants the next ring as ordinary incremental repair -- the full field
     * is paid for while moving instead of before it.
     */
    val planningHorizonChunks: Int get() = 4

    /** Longest tape the seed search may certify. */
    val maxFrames: Int

    /** Maximum client-thread time spent extending an immutable snapshot in one tick. */
    val snapshotCaptureBudgetMillis: Double get() = 3.0

    /** How close to the goal centre the walk must come to a stable stop. */
    val goalRadius: Double

    /** Entering the goal cell counts as arriving; the body brakes wherever it lands nearby. */
    val touchArrival: Boolean

    /** What reaching an intermediate waypoint of a route means; the final goal always rests. */
    val waypointArrival: com.lambda.pathing.core.ArrivalMode

    /**
     * Committed motion kept ahead of the body before another commitment is made.
     *
     * The search runs continuously and only commits when the runway gets this short, so
     * this is really "how late may a decision be left". Later is better -- every tick not
     * yet committed is a tick the search is still improving -- but too late and a slow
     * search lets the body reach the brake it is holding and stop.
     */
    val horizonRunwayFrames: Int get() = 20

    /** Motion each horizon step commits; the granularity at which the future is decided. */
    val horizonCommitFrames: Int get() = 20

    /**
     * On a refusal, write the whole captured plan — collision shapes, endpoints, entry
     * state, physics profile — to `neolambda/pathing-dumps`. The terrain behind a field
     * refusal otherwise exists only in the reporter's world; this turns it into a JVM
     * fixture that replays in a second.
     */
    val dumpFailedPlans: Boolean get() = false

    /** Deepest fall the trajectory layer will certify as survivable on ordinary ground. */
    val maxSafeFallDistance: Double get() = 3.0

    /** Hard cap on coarse D* expansions per repair before the search gives up. */
    val coarseExpansionBudget: Int get() = 1_000_000

    /** Hard cap on trajectory-search expansions per leg before the search gives up. */
    val trajectoryExpansionBudget: Int get() = 2_000_000

    /** Node budget of the reachable-frontier sweep that mints optimistic route terminals. */
    val frontierSweepBudget: Int get() = 40_000

    /** Minimum wall time before the first partial tape may be published. */
    val bootstrapDelayMillis: Int get() = 200

    /**
     * Rollout worker threads for the trajectory search. 1 is the serial search; above
     * it, each expansion batch simulates that many candidate movements concurrently.
     */
    val plannerThreads: Int get() = 1

    /**
     * Rollouts a solved plan may spend having its worst spans shortcut before it is
     * certified. 0 publishes the search's answer unchanged.
     */
    val improvementBudget: Int get() = 1500

    val momentumGait: Boolean get() = false

    val momentumSkips: Boolean get() = false

    /** Retries while the exact world capture catches up to a body at the streamed frontier. */
    val captureRetries: Int get() = 4

    /** Wait between those capture retries. */
    val captureRetryMillis: Int get() = 400
}
