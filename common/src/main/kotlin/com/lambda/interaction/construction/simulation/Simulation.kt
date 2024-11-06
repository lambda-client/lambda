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

import com.lambda.context.SafeContext
import com.lambda.interaction.construction.Blueprint
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.threading.runSafe
import com.lambda.util.Communication.info
import com.lambda.util.world.FastVector
import com.lambda.util.world.toBlockPos
import com.lambda.util.world.toFastVec
import com.lambda.util.world.toVec3d
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d

data class Simulation(val blueprint: Blueprint) {
    private val cache: MutableMap<FastVector, Set<BuildResult>> = mutableMapOf()
    private fun FastVector.toView(): Vec3d = toVec3d().add(0.5, 0.62, 0.5)
//    private lateinit var player: ClientPlayerEntity

//    init {
//        runSafe {
//            this@Simulation.player = player
//            BlockPos.iterateOutwards(player.blockPos, 5, 5, 5).forEach { pos ->
//                val vec = pos.toFastVec()
//                info("Preloading simulation at $vec")
//                simulate(vec)
//            }
//        }
//    }

//    val best: FastVector? get() = cache.filter { (_, results) ->
//        results.isNotEmpty() && results.any { it.rank.ordinal < 4 }
//    }.keys.minByOrNull { it.toVec3d().distanceTo(player.pos) }

//    fun best() = cache.filter { (_, results) ->
//        results.isNotEmpty() && results.any { it.rank.ordinal < 3 }
//    }.keys

    fun simulate(pos: FastVector): Set<BuildResult> {
//        runSafe {
//            if (!playerFitsIn(Vec3d.ofBottomCenter(pos.toBlockPos()))) return emptySet()
//        }
//        return blueprint.simulate(pos.toView()).also { cache[pos] = it }
//        return cache.computeIfAbsent(pos) {
////            runSafe {
////                if (!playerFitsIn(Vec3d.ofBottomCenter(pos.toBlockPos()))) return@computeIfAbsent emptySet()
////            }
//            blueprint.simulate(pos.toView())
//        }
        return blueprint.simulate(pos.toView(), reach = 3.5)
    }

    private fun SafeContext.playerFitsIn(pos: Vec3d): Boolean {
        val pBox = player.boundingBox
        val aabb = Box(pBox.minX, pBox.minY - 1.0E-6, pBox.minZ, pBox.maxX, pBox.minY, pBox.maxZ)
        return world.isSpaceEmpty(aabb.offset(pos))
    }

    companion object {
        fun Blueprint.simulation() = Simulation(this)
    }
}
