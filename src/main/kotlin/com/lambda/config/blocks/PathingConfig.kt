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

    /** How far the simulated walk may stray from the coarse route before rejection. */
    val maxCorridorDeviation: Double

    /**
     * Certify one corridor-adherent gait (with its launch beam) before sweeping the
     * full gait grid. Much faster discovery on long routes; the full sweep still runs
     * for any segment the adherent gait cannot solve.
     */
    val corridorAdherentFirst: Boolean

    /** How close to the goal centre the walk must come to a stable stop. */
    val goalRadius: Double

    /**
     * Discover the trajectory with the kinodynamic anchor search instead of the
     * whole-route gait sweep. Local certified transitions between exact grounded body
     * states, so an extra hazard costs a bounded amount of work rather than replaying
     * every earlier launch combination. A default member so existing config
     * implementors keep compiling.
     */
    val anchorSearch: Boolean get() = true

    /**
     * Steer the anchor search by the coarse cost-to-go *field* instead of the one route
     * D* extracts from it, and drop the corridor-deviation veto. The trajectory is then
     * free to leave the greedy line wherever the physics is faster; going the wrong way
     * is priced by the value, not forbidden. Takes precedence over [anchorSearch].
     */
    val valueFieldSearch: Boolean get() = false

    /**
     * On a refusal, write the whole captured plan — collision shapes, endpoints, entry
     * state, physics profile — to `neolambda/pathing-dumps`. The terrain behind a field
     * refusal otherwise exists only in the reporter's world; this turns it into a JVM
     * fixture that replays in a second.
     */
    val dumpFailedPlans: Boolean get() = false

    /**
     * Discover the trajectory with the trained value-guided neural policy instead of
     * the seed search. The policy proposes; the exact simulator still certifies every
     * frame, so the published tape is as safe as any other. Off by default and a
     * default member so existing config implementors keep compiling.
     */
    val neuralDiscovery: Boolean get() = false

    /** Filesystem path to the ONNX-exported policy; null resolves the default location. */
    val neuralModelPath: String? get() = null
}
