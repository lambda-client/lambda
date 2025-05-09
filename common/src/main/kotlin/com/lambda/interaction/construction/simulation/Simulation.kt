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

import com.lambda.context.Configured
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.blueprint.Blueprint
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.Drawable
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.world.FastVector
import com.lambda.util.world.toBlockPos
import com.lambda.util.world.toVec3d
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.awt.Color

data class Simulation(val blueprint: Blueprint) {
    private val cache: MutableMap<FastVector, Set<BuildResult>> = mutableMapOf()
    private fun FastVector.toView(): Vec3d = toVec3d().add(0.5, ClientPlayerEntity.DEFAULT_EYE_HEIGHT.toDouble(), 0.5)

    fun simulate(pos: FastVector, configured: Configured) = cache.getOrPut(pos) {
        val view = pos.toView()
        val isOutOfBounds = blueprint.isOutOfBounds(view)
        val isTooFar = blueprint.getClosestPointTo(view).distanceTo(view) > 10.0
        runSafe {
            if (isOutOfBounds && isTooFar) return@getOrPut emptySet()
            val blockPos = pos.toBlockPos()
            val isWalkable = blockState(blockPos.down()).isSideSolidFullSquare(world, blockPos, Direction.UP)
            if (!isWalkable) return@getOrPut emptySet()
            if (!playerFitsIn(blockPos)) return@getOrPut emptySet()
        }

        configured.run {
            simulate(blueprint, view)
        }
    }

    fun goodPositions() = cache
        .filter { entry -> entry.value.any { it.rank.ordinal < 4 } }
        .map { PossiblePos(it.key.toBlockPos(), it.value.count { it.rank.ordinal < 4 }) }

    class PossiblePos(val pos: BlockPos, val interactions: Int): Drawable {
        override fun SafeContext.buildRenderer() {
            withBox(Vec3d.ofBottomCenter(pos).playerBox(), Color(0, 255, 0, 50))
        }
    }

    private fun SafeContext.playerFitsIn(pos: BlockPos): Boolean {
        return world.isSpaceEmpty(Vec3d.ofBottomCenter(pos).playerBox())
    }

    companion object {
        fun Vec3d.playerBox(): Box = Box(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3).contract(1.0E-6)

        fun Blueprint.simulation() = Simulation(this)
    }
}
