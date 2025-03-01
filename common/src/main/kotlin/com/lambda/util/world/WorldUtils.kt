/*
 * Copyright 2025 Lambda
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

package com.lambda.util.world

import com.lambda.context.SafeContext
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.math.flooredBlockPos
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.math.min

object WorldUtils {
    fun SafeContext.traversable(pos: BlockPos) =
        blockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP) && playerFitsIn(pos)

    fun SafeContext.isPathClear(
        start: BlockPos,
        end: BlockPos,
        stepSize: Double = 0.3,
    ) = isPathClear(Vec3d.ofBottomCenter(start), Vec3d.ofBottomCenter(end), stepSize)

    fun SafeContext.isPathClear(
        start: Vec3d,
        end: Vec3d,
        stepSize: Double = 0.3 // Step size based on player's hitbox radius
    ): Boolean {
        val direction = end.subtract(start)
        val distance = direction.length()
        if (distance <= 0) return true // No movement needed

        val stepDirection = direction.normalize().multiply(stepSize)
        var currentPos = start
        var remainingDistance = distance

        while (remainingDistance > 0) {
            val blockPos = currentPos.flooredBlockPos
            val goingDownwards = start.y > end.y
            val hasSupport = blockState(blockPos.down()).isSideSolidFullSquare(world, blockPos.down(), Direction.UP)
            if (!(playerFitsIn(currentPos) && (goingDownwards || hasSupport))) return false

            val step = min(stepSize, remainingDistance)
            currentPos = currentPos.add(stepDirection.multiply(step))
            remainingDistance -= step
        }

        // Final check at end position
        return playerFitsIn(end)
    }

    fun SafeContext.playerFitsIn(pos: Vec3d) =
        world.isSpaceEmpty(pos.playerBox())

    fun SafeContext.playerFitsIn(pos: BlockPos) =
        world.isSpaceEmpty(Vec3d.ofBottomCenter(pos).playerBox())

    fun Vec3d.playerBox(): Box =
        Box(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3).contract(1.0E-6)
}