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

    /** Longest tape the seed search may certify. */
    val maxFrames: Int

    /** How far the simulated walk may stray from the coarse route before rejection. */
    val maxCorridorDeviation: Double

    /** How close to the goal centre the walk must come to a stable stop. */
    val goalRadius: Double
}
