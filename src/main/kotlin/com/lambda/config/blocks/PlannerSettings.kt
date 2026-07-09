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

class PlannerSettings(override val c: Config) : PlannerConfig, ConfigBlock {
    companion object {
        private const val GENERAL_GROUP = "General"
        private const val EDGES_GROUP = "Edges"
    }

    @Group(GENERAL_GROUP) override val computeBudget by c.setting("Compute Budget", 50L, 1L..500L, 1L, "Maximum time the D* Lite repair step may spend per refresh before returning the best partial result so far.", " ms")
    @Group(GENERAL_GROUP) override val maxPathLength by c.setting("Max Path Length", 10_000, 100..100_000, 100, "Hard cap on the number of coarse nodes extracted from the planner for one traversal.")

    @Group(EDGES_GROUP) override val allowDiagonal by c.setting("Allow Diagonal", true, "Allow diagonal walking edges in the coarse walking graph.")
    @Group(EDGES_GROUP) override val allowVertical by c.setting("Allow Vertical", true, "Allow vertical walking edges in the coarse walking graph.")
    @Group(EDGES_GROUP) override val allowJump by c.setting("Allow Jump", false, "Allow jump successors (up to 2 blocks horizontally, 1 block up) in the coarse walking graph.") { allowVertical }
    @Group(EDGES_GROUP) override val maxDropHeight by c.setting("Max Drop Height", 3, 1..8, 1, "Maximum walk-off drop depth the planner may use. Drops deeper than 3 deal fall damage without mitigation.", " blocks") { allowVertical }
    @Group(EDGES_GROUP) override val allowManeuverDiscovery by c.setting("Maneuver Discovery", false, "Discover sprint-jump edges beyond the template range at platform ledges (simulation-validated, up to ~4 blocks). Experimental — WP3.2.") { allowJump }
}
