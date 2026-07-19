
package com.minato.util.world

import com.minato.context.SafeContext
import com.minato.util.extension.getBlockState
import com.minato.util.extension.getFluidState
import com.minato.util.world.WorldUtils.internalGetEntities
import com.minato.util.world.WorldUtils.internalGetFastEntities
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.Entity
import net.minecraft.fluid.Fluid
import net.minecraft.fluid.FluidState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos
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
        pos: FastVector,
        distance: Double,
        crossinline filter: (T) -> Boolean = { true },
    ): Sequence<T> {
        val chunks = ceil(distance / 16).toInt()
        val sectionX = pos.x shr 4
        val sectionY = pos.y shr 4
        val sectionZ = pos.z shr 4

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
                                    it != player &&
                                            pos distSq it.pos <= distance * distance &&
                                            filter(it)
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
        pos: FastVector,
        distance: Double,
        crossinline filter: (T) -> Boolean = { true },
    ) = world.entities
        .asSequence()
        .filterIsInstance<T>()
        .filter {
            it != player &&
                    pos distSq it.pos <= distance * distance &&
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
            val state = world.getBlockState(it)
            filter(it, state)
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

