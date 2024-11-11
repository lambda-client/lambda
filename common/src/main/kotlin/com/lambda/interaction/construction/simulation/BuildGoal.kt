/*
 * Copyright 2024 Lambda
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

package com.lambda.interaction.construction.simulation

import baritone.api.pathing.goals.Goal
import com.lambda.util.world.fastVectorOf

class BuildGoal(private val sim: Simulation) : Goal {
    override fun isInGoal(x: Int, y: Int, z: Int): Boolean {
        val vec = fastVectorOf(x, y, z)
        val simu = sim.simulate(vec)
        return simu.any { it.rank.ordinal < 4 }
    }

    override fun heuristic(x: Int, y: Int, z: Int): Double {
        val bestRank = sim.simulate(fastVectorOf(x, y, z)).minOrNull()?.rank?.ordinal ?: 100000
        val score = 1 / (bestRank.toDouble() + 1)
//        info("Calculating heuristic at $x, $y, $z with score $score")
        return score
    }
}
