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

package com.lambda.interaction.request.rotation.visibilty

import com.lambda.interaction.request.rotation.Rotation
import com.lambda.interaction.request.rotation.RotationManager
import com.lambda.interaction.request.rotation.visibilty.VisibilityChecker.ALL_SIDES
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import com.lambda.util.world.raycast.RayCastUtils.entityResult
import net.minecraft.entity.LivingEntity
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction

/**
 * An abstract class representing a rotation target, which can be either a block or an entity.
 */
abstract class RequestedHit {
    abstract val sides: Set<Direction>
    abstract val reach: Double

    /**
     * Verifies if the given [HitResult] satisfies the criteria of this
     * requested hit.
     *
     * @param hit The [HitResult] to be verified.
     * @return True if the hit satisfies the criteria, false otherwise.
     */
    abstract fun verifyHit(hit: HitResult): Boolean

    /**
     * Gets a list of bounding boxes associated with this requested hit.
     *
     * @return A list of [Box] objects representing the bounding boxes.
     */
    abstract fun getBoundingBoxes(): List<Box>

    /**
     * Validates the hit based on the current rotation and reach.
     *
     * @param rotation The rotation to be checked.
     *
     * @return True if the hit is valid, false otherwise.
     */
    fun verifyRotation(rotation: Rotation = RotationManager.currentRotation) =
        rotation.rayCast(reach)?.let { verifyHit(it) } ?: false

    /**
     * Validates the hit based on the current rotation and reach and returns the hit result if passed.
     *
     * @return [HitResult] if passed, null otherwise.
     */
    fun hitIfValid() =
        RotationManager.currentRotation.rayCast(reach)?.let {
            if (!verifyHit(it)) null else it
        }

    /**
     * Represents an entity hit request.
     *
     * @param entity The [LivingEntity] to be hit.
     * @param reach The maximum distance for the hit.
     */
    data class Entity(
        val entity: LivingEntity,
        override val reach: Double,
        override val sides: Set<Direction> = ALL_SIDES
    ) : RequestedHit() {
        override fun getBoundingBoxes() =
            listOf(entity.boundingBox)

        override fun verifyHit(hit: HitResult) =
            hit.entityResult?.entity == entity
    }

    /**
     * Represents a block hit request
     *
     * @param blockPos The position of the block.
     * @param sides The set of directions for which the hit is requested.
     * @param reach The maximum distance for the hit.
     */
    data class Block(
        val blockPos: BlockPos,
        override val sides: Set<Direction>,
        override val reach: Double
    ) : RequestedHit() {
        override fun getBoundingBoxes(): List<Box> = runSafe {
            val state = blockState(blockPos)
            val voxelShape = state.getOutlineShape(world, blockPos)
            voxelShape.boundingBoxes.map { it.offset(blockPos) }
        } ?: listOf(Box(blockPos))

        override fun verifyHit(hit: HitResult) =
            hit.blockResult?.let {
                it.blockPos == blockPos && (sides.isEmpty() || it.side in sides)
            } ?: false
    }
}