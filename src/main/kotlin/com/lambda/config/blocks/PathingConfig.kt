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

    /** Longest tape the seed search may certify. */
    val maxFrames: Int

    /** Maximum client-thread time spent extending an immutable snapshot in one tick. */
    val snapshotCaptureBudgetMillis: Double get() = 3.0

    /** How close to the goal centre the walk must come to a stable stop. */
    val goalRadius: Double

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

}
