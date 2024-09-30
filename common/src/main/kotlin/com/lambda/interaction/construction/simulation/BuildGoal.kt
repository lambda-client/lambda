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