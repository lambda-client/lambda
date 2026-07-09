/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.config.blocks

import com.lambda.config.Config
import com.lambda.config.ConfigBlock
import com.lambda.config.Group

class PathRefinementSettings(override val c: Config) : PathRefinementConfig, ConfigBlock {
    companion object {
        private const val GENERAL_GROUP = "General"
    }

    @Group(GENERAL_GROUP) override val enabled by c.setting("Refinement Enabled", true, "Enable post-processing that replaces runs of same-height coarse nodes with validated any-angle segments.")
    @Group(GENERAL_GROUP) override val clearanceMargin by c.setting("Clearance Margin", 0.05, 0.0..0.30, 0.005, "Extra horizontal margin added to the corridor footprint so shortcuts do not graze block corners.", " blocks")
    @Group(GENERAL_GROUP) override val maxLookahead by c.setting("Max Lookahead", 64, 1..512, 1, "Maximum number of future coarse nodes one anchor may try to skip over while searching for the farthest valid shortcut.")
    @Group(GENERAL_GROUP) override val maxChecks by c.setting("Max Checks", 2_048, 1..20_000, 1, "Budget for shortcut validation attempts during one refinement pass. When exhausted, the remaining coarse path is kept as-is.")
}
