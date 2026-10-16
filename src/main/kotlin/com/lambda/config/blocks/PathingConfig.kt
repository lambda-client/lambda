/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.config.blocks

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.core.ArrivalMode
import com.lambda.pathing.world.snapshot.HazardRules

/**
 * What the coarse planner and the trajectory search are allowed to do.
 *
 * This rides on [com.lambda.context.Automated], so a module that requests a walk
 * plans under its own settings, exactly like its rotation config.
 */
interface PathingConfig {
    val allowDiagonal: Boolean
    val allowStepUp: Boolean
    val allowSprint: Boolean
    val saveFood: Boolean
    val maxWalkOffDepth: Int
    val maxDropSpan: Int
    val allowClimbing: Boolean
    val allowSwimming: Boolean
    val allowDiving: Boolean
    val diveSeconds: Double
    val unlimitedAir: Boolean
    val allowJumpCandidates: Boolean
    val maxJumpSpan: Int
    val maxJumpDrop: Int
    val allowOffAxisJumps: Boolean
    val allowDeepDropJumps: Boolean
    val allowNeoJumps: Boolean
    val allowSlimeBounces: Boolean
    val maxBounceDrop: Int
    val allowDeepFalls: Boolean

    val avoidFire: Boolean
    val avoidCacti: Boolean
    val sneakOnMagma: Boolean
    val avoidLavaEdges: Boolean
    val avoidMobs: Boolean
    val mobLingerSeconds: Double
    val mobWaitSeconds: Double
    val allowFallDamage: Boolean
    val maxFallDamage: Double
    val unlimitedFallDamage: Boolean

    val goalRadius: Double
    val touchArrival: Boolean
    val touchRestRadius: Double
    val waypointArrival: ArrivalMode

    val frontierTicksPerBlock: Double
    val horizonRunwayFrames: Int
    val horizonCommitFrames: Int
    val coarseExpansionBudget: Int
    val windowExpansionBudget: Int
    val bootstrapDelayMillis: Int
    val plannerThreads: Int

    val snapshotCaptureBudgetMillis: Double
    val captureRetries: Int
    val captureRetryMillis: Int
    val useBaritoneCache: Boolean

    val dumpFailedPlans: Boolean
    val dumpAllPlans: Boolean

    val maxFrames: Int get() = MotionConstraints.DEFAULT_MAX_FRAMES

    val hazards: HazardRules get() = HazardRules(avoidFire, avoidCacti, sneakOnMagma)
}
