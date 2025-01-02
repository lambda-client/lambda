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
import com.lambda.interaction.construction.blueprint.Blueprint
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.world.FastVector
import com.lambda.util.world.toBlockPos
import com.lambda.util.world.toVec3d
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

data class Simulation(val blueprint: Blueprint) {
    private val cache: MutableMap<FastVector, Set<BuildResult>> = mutableMapOf()
    private fun FastVector.toView(): Vec3d = toVec3d().add(0.5, ClientPlayerEntity.DEFAULT_EYE_HEIGHT.toDouble(), 0.5)

    fun simulate(pos: FastVector) =
        cache.getOrPut(pos) {
            val view = pos.toView()
            runSafe {
                if (blueprint.isOutOfBounds(view) && blueprint.getClosestPointTo(view)
                        .distanceTo(view) > 10.0
                ) return@getOrPut emptySet()
                val blockPos = pos.toBlockPos()
                if (!playerFitsIn(Vec3d.ofBottomCenter(blockPos))) return@getOrPut emptySet()
                if (!blockPos.down().blockState(world)
                        .isSideSolidFullSquare(world, blockPos, Direction.UP)
                ) return@getOrPut emptySet()
            }
            blueprint.simulate(view, reach = TaskFlowModule.interact.reach - 1)
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
