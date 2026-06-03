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

package com.lambda.util.world

import com.lambda.context.SafeContext
import com.lambda.util.extension.getBlockState
import com.lambda.util.extension.getFluidState
import com.lambda.util.math.F_ONE
import com.lambda.util.math.FastVector
import com.lambda.util.math.distSq
import com.lambda.util.math.distanceToSideSq
import com.lambda.util.math.fastVectorOf
import com.lambda.util.math.plus
import com.lambda.util.math.times
import com.lambda.util.math.x
import com.lambda.util.math.y
import com.lambda.util.math.z
import com.lambda.util.world.WorldUtils.internalGetEntities
import com.lambda.util.world.WorldUtils.internalGetFastEntities
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.Entity
import net.minecraft.fluid.Fluid
import net.minecraft.fluid.FluidState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.Vec3d
import kotlin.math.ceil

object WorldUtils {
    fun SafeContext.isLoaded(pos: BlockPos) =
        world.chunkManager.isChunkLoaded(
            ChunkSectionPos.getSectionCoord(pos.x), ChunkSectionPos.getSectionCoord(pos.z)
        )

    /**
     * Returns a sequence of entities.
     *
     * This implementation is optimized for performance at small distances.
     * For distances larger than 64 blocks, it is recommended to use the [internalGetEntities] function instead.
     *
     * @see [fastEntitySearch]
     */
    inline fun <reified T : Entity> SafeContext.internalGetFastEntities(
	    pos: Vec3d,
	    distance: Double,
	    crossinline filter: (T) -> Boolean = { true },
    ): Sequence<T> {
        val chunks = ceil(distance / 16).toInt()
        val sectionX = (pos.x / 4).toInt()
        val sectionY = (pos.y / 4).toInt()
        val sectionZ = (pos.z / 4).toInt()

        return sequence {
            for (x in sectionX - chunks..sectionX + chunks) {
                for (y in sectionY - chunks..sectionY + chunks) {
                    for (z in sectionZ - chunks..sectionZ + chunks) {
                        yieldAll(
                            world.entityManager.cache
                                .findTrackingSection(ChunkSectionPos.asLong(x, y, z))
                                ?.collection
                                ?.asSequence()
                                ?.filterIsInstance<T>()
                                ?.filter {
                                    it != player
                                            && pos.distanceToSideSq(it.boundingBox) <= distance * distance
                                            && filter(it)
                                } ?: emptySequence()
                        )
                    }
                }
            }
        }
    }

    /**
     * Returns a sequence of entities.
     * Unlike [internalGetFastEntities], it traverses all entities in the world to find matches.
     *
     * @see [entitySearch]
     */
    inline fun <reified T : Entity> SafeContext.internalGetEntities(
	    pos: Vec3d,
	    distance: Double,
	    crossinline filter: (T) -> Boolean = { true },
    ) = world.entities
        .asSequence()
        .filterIsInstance<T>()
        .filter {
            it != player &&
                    pos.distanceToSideSq(it.boundingBox) <= distance * distance &&
                    filter(it)
        }

    /**
     * Returns a sequence of block entities.
     * @see [blockEntitySearch]
     */
    inline fun <reified T : BlockEntity> SafeContext.internalGetBlockEntities(
	    pos: FastVector,
	    distance: Double,
	    crossinline predicate: (T) -> Boolean = { true },
    ): Sequence<T> {
        val chunks = ceil(distance / 16).toInt()
        val chunkX = pos.x shr 4
        val chunkZ = pos.z shr 4

        return sequence {
            for (x in chunkX - chunks..chunkX + chunks) {
                for (z in chunkZ - chunks..chunkZ + chunks) {
                    val chunk = world.getChunk(x, z)

                    yieldAll(
                        chunk.blockEntities
                            .values
                            .asSequence()
                            .filterIsInstance<T>()
                            .filter {
                                pos distSq it.pos <= distance * distance &&
                                        predicate(it)
                            }
                    )
                }
            }
        }
    }

    /**
     * Returns a map of positions to block states.
     * @see [blockSearch]
     */
    inline fun SafeContext.internalSearchBlocks(
	    pos: FastVector,
	    range: FastVector = F_ONE times 7,
	    step: FastVector = F_ONE,
	    crossinline filter: (FastVector, BlockState) -> Boolean = { _, _ -> true },
    ) = fastSequence(pos, range, step)
        .filter {
            filter(it,
                world.getBlockState(it))
        }
        .associateWith { world.getBlockState(it) }


    /**
     * Returns a map of positions to fluid states.
     * @see [fluidSearch]
     */
    inline fun <reified T : Fluid> SafeContext.internalSearchFluids(
	    pos: FastVector,
	    range: FastVector = F_ONE times 7,
	    step: FastVector = F_ONE,
	    crossinline filter: (FastVector, FluidState) -> Boolean = { _, _ -> true },
    ) = fastSequence(pos, range, step)
        .filter {
            val state = world.getFluidState(it.x, it.y, it.z)
            state.fluid is T &&
                    filter(it, state)
        }
        .associateWith { world.getFluidState(it) }

    /**
     * Returns a sequence of [FastVector]s
     */
    fun fastSequence(
	    pos: FastVector,
	    range: FastVector,
	    step: FastVector,
    ) = sequence {
        for (x in -range.x..range.x step step.x) {
            for (y in -range.y..range.y step step.y) {
                for (z in -range.z..range.z step step.z) {
                    yield(pos plus fastVectorOf(x, y, z))
                }
            }
        }
    }
}

