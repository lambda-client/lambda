/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.config.blocks

import com.lambda.config.Config
import com.lambda.config.ConfigBlock
import com.lambda.config.Group

class PathingSettings(override val c: Config) : PathingConfig, ConfigBlock {
    @Group(MOVES_GROUP)
    override val allowDiagonal by c.setting("Diagonal Moves", true, "Allow 45-degree coarse edges.")

    @Group(MOVES_GROUP)
    override val allowStepUp by c.setting("Step Up", true, "Allow one-block rises. They are jumped, not walked.")

    @Group(MOVES_GROUP)
    override val allowSprint by c.setting("Allow Sprint", true, "Let the seed search try sprinting gaits.")

    @Group(SEARCH_GROUP)
    override val maxFrames by c.setting(
        "Max Frames", 160, 40..600, 10,
        "Longest tape the seed search may certify.", unit = " ticks",
    )

    @Group(SEARCH_GROUP)
    override val maxCorridorDeviation by c.setting(
        "Corridor Deviation", 1.5, 0.5..4.0, 0.1,
        "How far the simulated walk may stray from the coarse route before it is rejected.",
        unit = " blocks",
    )

    @Group(SEARCH_GROUP)
    override val goalRadius by c.setting(
        "Goal Radius", 0.20, 0.05..1.0, 0.01,
        "How close to the goal centre the walk must stop.", unit = " blocks",
    )

    private companion object {
        const val MOVES_GROUP = "Moves"
        const val SEARCH_GROUP = "Seed Search"
    }
}
