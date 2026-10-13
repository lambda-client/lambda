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

    /** Spend as little hunger as possible: never sprint and jump only where a jump buys real time. */
    val saveFood: Boolean get() = false

    /** Ticks a far goal charges per block of terrain nobody has loaded yet; higher heads straighter for the goal. */
    val frontierTicksPerBlock: Double get() = com.lambda.pathing.actions.SimpleMoveOptions.DEFAULT_FRONTIER_TICKS_PER_BLOCK

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

    /** Let the graph route across water at the surface, in from the shore and out onto banks. */
    val allowSwimming: Boolean get() = true

    /** Let the graph route under water, through flooded passages and shafts that stay within reach of air. */
    val allowDiving: Boolean get() = true

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
     * Whether jumps that land off the eight compass rays, and slime bounces at any heading, are offered.
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
     * block the wide diagonals, a block and three quarters a straight five-cell gap from
     * momentum. On by default: over 35 bedrock routes the frames were unchanged (-0.1%,
     * per-route spread 4.4%), at a little more stalling and coarse time.
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
     * Whether the coarse layer offers falls deeper than a walk-off, from any rim down to water,
     * hay bales and beds, or to any floor the accepted fall damage allows. Landings are found by
     * scanning the columns below the rim rather than by templates, so the depth has no cap.
     */
    val allowDeepFalls: Boolean get() = true

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
     * On a refusal, replan, hold, stop or cancel, write the plan — endpoints, entry state, physics
     * profile, the executed tape around the event and the sections it captured — gzipped to
     * `lambda/pathing-dumps`, with the block states of those sections
     * in `lambda/cache/terrain`. The terrain behind a field refusal otherwise exists only in the
     * reporter's world; this turns it into a JVM fixture that replays in a second.
     */
    val dumpFailedPlans: Boolean get() = false

    /**
     * Write the same dump for every planned leg, not only refusals, with the certified
     * frame count in the note. Turns a real course into an offline fixture the improver
     * and the slack audit can be measured against.
     */
    val dumpAllPlans: Boolean get() = false

    /**
     * Plan beyond loaded chunks through the terrain Baritone's cache remembers, read only:
     * oceans, mountains and ravines from earlier trips shape the coarse route, and every chunk
     * captured exactly replaces what the cache remembered of it.
     */
    val useBaritoneCache: Boolean get() = true

    /** Fall damage in hearts the planner may take on one landing; falls the player takes unhurt are always used. */
    val maxFallDamage: Double get() = 0.0

    /**
     * Treat fire, lit campfires and cacti as the blocks they are instead of cells to keep out of:
     * the walk passes through fire and stands on or brushes past cacti, taking the damage.
     */
    val ignoreHazards: Boolean get() = false

    /** Space in blocks kept between the body and lava, below the feet and beside the body; 0 follows vanilla exactly. */
    val lavaMargin: Double get() = 0.0

    /**
     * Keep out of the cells nearby mobs, animals and other players stand in, for a few seconds after
     * they leave them, so walks go around them instead of being pushed.
     */
    val avoidMobs: Boolean get() = true

    /** Hard cap on coarse D* expansions per repair before the search gives up. */
    val coarseExpansionBudget: Int get() = 1_000_000

    /** Hard cap on trajectory-search expansions per leg before the search gives up. */
    val trajectoryExpansionBudget: Int get() = 2_000_000


    /** Minimum wall time before the first partial tape may be published. */
    val bootstrapDelayMillis: Int get() = 200

    /**
     * Rollout worker threads for the trajectory search. 1 is the serial search; above
     * it, each expansion batch simulates that many candidate movements concurrently.
     */
    val plannerThreads: Int get() = 1

    /** Retries while the exact world capture catches up to a body at the streamed frontier. */
    val captureRetries: Int get() = 4

    /** Wait between those capture retries. */
    val captureRetryMillis: Int get() = 400
}
