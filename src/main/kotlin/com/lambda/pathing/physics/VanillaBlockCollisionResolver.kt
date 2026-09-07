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

package com.lambda.pathing.physics

import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes

internal object VanillaBlockCollisionResolver {
    fun adjust(
        movement: Vec3d,
        boundingBox: Box,
        onGround: Boolean,
        stepHeight: Double,
        collisionShapes: (Box) -> List<VoxelShape>,
    ): Vec3d {
        if (movement.lengthSquared() == 0.0) return movement

        val normalShapes = collisionShapes(boundingBox.stretch(movement))
        val normal = clip(movement, boundingBox, normalShapes)
        val xBlocked = movement.x != normal.x
        val yBlocked = movement.y != normal.y
        val zBlocked = movement.z != normal.z
        val landedDuringMove = yBlocked && movement.y < 0.0

        if (stepHeight <= 0.0 || (!landedDuringMove && !onGround) || (!xBlocked && !zBlocked)) {
            return normal
        }

        val stepBase = if (landedDuringMove) boundingBox.offset(0.0, normal.y, 0.0) else boundingBox
        var stepQuery = stepBase.stretch(movement.x, stepHeight, movement.z)
        if (!landedDuringMove) {
            stepQuery = stepQuery.stretch(0.0, (-1.0E-5F).toDouble(), 0.0)
        }

        val stepShapes = collisionShapes(stepQuery)
        val baseVerticalOffset = normal.y.toFloat()
        for (candidateHeight in collectStepHeights(stepBase, stepShapes, stepHeight.toFloat(), baseVerticalOffset)) {
            val candidate = clip(
                movement = Vec3d(movement.x, candidateHeight.toDouble(), movement.z),
                boundingBox = stepBase,
                collisions = stepShapes,
            )
            if (candidate.horizontalLengthSquared() > normal.horizontalLengthSquared()) {
                val landingAdjustment = boundingBox.minY - stepBase.minY
                return candidate.subtract(0.0, landingAdjustment, 0.0)
            }
        }

        return normal
    }

    private fun clip(
        movement: Vec3d,
        boundingBox: Box,
        collisions: List<VoxelShape>,
    ): Vec3d {
        if (collisions.isEmpty()) return movement

        var adjusted = Vec3d.ZERO
        for (axis in Direction.getCollisionOrder(movement)) {
            val component = movement.getComponentAlongAxis(axis)
            if (component == 0.0) continue
            val offset = VoxelShapes.calculateMaxOffset(
                axis,
                boundingBox.offset(adjusted),
                collisions,
                component,
            )
            adjusted = adjusted.withAxis(axis, offset)
        }
        return adjusted
    }

    private fun collectStepHeights(
        collisionBox: Box,
        collisions: List<VoxelShape>,
        maximumStepHeight: Float,
        baseVerticalOffset: Float,
    ): FloatArray {
        val heights = HashSet<Float>(4)
        for (shape in collisions) {
            val points = shape.getPointPositions(Direction.Axis.Y)
            for (index in points.indices) {
                val point = points.getDouble(index)
                val height = (point - collisionBox.minY).toFloat()
                if (height < 0.0F || height == baseVerticalOffset) continue
                if (height > maximumStepHeight) break
                heights += height
            }
        }
        return heights.sorted().toFloatArray()
    }
}
