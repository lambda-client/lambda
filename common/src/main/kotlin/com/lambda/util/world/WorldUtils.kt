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

package com.lambda.util.world

import com.lambda.context.SafeContext
import com.lambda.core.annotations.InternalApi
import com.lambda.util.extension.filterPointer
import com.lambda.util.extension.getBlockState
import com.lambda.util.extension.getFluidState
import net.minecraft.block.BlockState
import net.minecraft.entity.Entity
import net.minecraft.fluid.Fluid
import net.minecraft.fluid.FluidState
import net.minecraft.util.math.ChunkSectionPos
import kotlin.math.ceil
import kotlin.reflect.KClass

/**
 * Utility functions for working with the Minecraft world.
 *
 * This object employs a pass-by-reference model, allowing functions to modify
 * data structures passed to them rather than creating new ones. This approach
 * offers two main benefits:
 *
 * - **Performance**: Pass-by-reference avoids unnecessary memory allocations
 * and reallocations that can occur when creating new data structures.
 *
 * - **Reduced Garbage Collection Overhead**: In languages with garbage collection
 * like Kotlin, creating and discarding many temporary objects can lead to increased
 * overhead. Pass-by-reference helps mitigate this by minimizing the creation of
 * temporary objects.
 *
 * When you create a new object, the JVM allocates memory for it on the heap.
 * When it is no longer necessary, the garbage collector frees up the memory.
 * This process **IS** expensive, especially if you are creating and discarding many objects
 *
 * Please note that the author of this code currently does not have any certifications in the field of computer science.
 * Simply plain old experience and knowledge.
 *
 * @see <a href="https://www.ibm.com/docs/en/i/7.4?topic=calls-pass-by-reference">IBM - Pass By Reference</a>
 * @see <a href="https://www.cs.fsu.edu/~myers/c++/notes/references.html">Florida State University - Pass By Reference vs. Pass By Value</a>
 * @see <a href="https://www.ibm.com/docs/no/aix/7.2?topic=monitoring-garbage-collection-impacts-java-performance">IBM - Garbage Collection Impacts on Java Performance</a>
 * @see <a href="https://devdiaries.medium.com/gc-and-its-effect-on-java-performance-9cba51ffb196">Medium - GC and Its Effect on Java Performance</a>
 */
object WorldUtils {
    /**
     * A magic vector that can be used to represent a single block.
     * It is the same as `fastVectorOf(1, 1, 1)`.
     */
    @InternalApi
    const val MAGICVECTOR = 274945015809L

    /**
     * Gets all entities of type [T] within a specified distance from a position.
     *
     * This function retrieves entities of type [T] within a specified distance from a given position. It efficiently
     * queries nearby chunks based on the distance and returns a list of matching entities, excluding the player entity.
     *
     * Examples:
     * - Getting all hostile entities within a certain distance:
     * ```
     * val hostileEntities = mutableListOf<HostileEntity>()
     * getFastEntities<HostileEntity>(player.pos, 30.0, hostileEntities)
     * ```
     *
     * Please note that this implementation is optimized for performance at small distances.
     * For larger distances, it is recommended to use the [internalGetEntities] function instead.
     * With the time complexity, we can determine that the performance of this function will degrade after 64 blocks.
     *
     * @param pos The position to search from.
     * @param distance The maximum distance to search for entities.
     * @param pointer The mutable list to store the entities in.
     * @param iterator Iterator to perform operations on each entity. The second parameter is the index of the iteration.
     * @param predicate Predicate to filter entities.
     */
    @InternalApi
    inline fun <reified T : Entity> SafeContext.internalGetFastEntities(
        pos: FastVector,
        distance: Double,
        pointer: MutableList<T>? = null,
        predicate: (T) -> Boolean = { true },
        iterator: (T) -> Unit = { _ -> },
    ) = internalGetFastEntities(T::class, pos, distance, pointer, predicate, iterator)

    @InternalApi
    inline fun <T : Entity> SafeContext.internalGetFastEntities(
        kClass: KClass<out T>,
        pos: FastVector,
        distance: Double,
        pointer: MutableList<T>? = null,
        predicate: (T) -> Boolean = { true },
        iterator: (T) -> Unit = { _ -> },
    ) {
        val chunks = ceil(distance / 16.0).toInt()
        val sectionX = pos.x shr 4
        val sectionY = pos.y shr 4
        val sectionZ = pos.z shr 4

        // Here we iterate over all sections within the specified distance and add all entities of type [T] to the list.
        // We do not have to worry about performance here, as the number of sections is very limited.
        // For example, if the player is on the edge of a section and the distance is 16, we only have to iterate over 9 sections.
        for (x in sectionX - chunks..sectionX + chunks) {
            for (y in sectionY - chunks..sectionY + chunks) {
                for (z in sectionZ - chunks..sectionZ + chunks) {
                    val section = world
                        .entityManager
                        .cache
                        .findTrackingSection(ChunkSectionPos.asLong(x, y, z)) ?: continue

                    section.collection.filterPointer(kClass, pointer, iterator) { entity ->
                        entity != player &&
                                pos distSq entity.pos <= distance * distance &&
                                predicate(entity)
                    }
                }
            }
        }
    }

    /**
     * Gets all entities of type [T] within a specified distance from a position.
     *
     * This function retrieves entities of type [T] within a specified distance from a given position. Unlike
     * [internalGetFastEntities], it traverses all entities in the world to find matches, while also excluding the player entity.
     *
     * @param pos The block position to search from.
     * @param distance The maximum distance to search for entities.
     * @param pointer The mutable list to store the entities in.
     * @param iterator Iterator to perform operations on each entity. The second parameter is the index of the iteration.
     * @param predicate Predicate to filter entities.
     */
    @InternalApi
    inline fun <reified T : Entity> SafeContext.internalGetEntities(
        pos: FastVector,
        distance: Double,
        pointer: MutableList<T>? = null,
        predicate: (T) -> Boolean = { true },
        iterator: (T) -> Unit = { _ -> },
    ) = internalGetEntities(T::class, pos, distance, pointer, predicate, iterator)

    @InternalApi
    inline fun <T : Entity> SafeContext.internalGetEntities(
        kClass: KClass<out T>,
        pos: FastVector,
        distance: Double,
        pointer: MutableList<T>? = null,
        predicate: (T) -> Boolean = { true },
        iterator: (T) -> Unit = { _ -> },
    ) {
        world.entities.filterPointer(kClass, pointer, iterator) { entity ->
            entity != player &&
                    pos distSq entity.pos <= distance * distance &&
                    predicate(entity)
        }
    }

    /**
     * Returns all the blocks and positions within the range where the predicate is true.
     *
     * @param pos The position to search from.
     * @param range The maximum distance to search for entities in each axis.
     * @param pointer The mutable map to store the positions to blocks in.
     * @param iterator Iterator to perform operations on each block.
     * @param predicate Predicate to filter the blocks.
     */
    @InternalApi
    inline fun SafeContext.internalSearchBlocks(
        pos: FastVector,
        range: FastVector = MAGICVECTOR times 7,
        step: FastVector = MAGICVECTOR,
        pointer: MutableMap<FastVector, BlockState>? = null,
        predicate: (FastVector, BlockState) -> Boolean = { _, _ -> true },
        iterator: (FastVector, BlockState) -> Unit = { _, _ -> },
    ) {
        internalIteratePositions(pos, range, step) { position ->
            world.getBlockState(position).let { state ->
                val fulfilled = predicate(position, state)

                if (fulfilled && pointer != null) {
                    pointer[position] = state
                    iterator(position, state)
                }
            }
        }
    }

    /**
     * Returns all the position within the range where the predicate is true.
     *
     * @param pos The position to search from.
     * @param range The maximum distance to search for fluids in each axis.
     * @param pointer The mutable list to store the positions in.
     * @param iterator Iterator to perform operations on each fluid.
     * @param predicate Predicate to filter the fluids.
     */
    @InternalApi
    inline fun <reified T : Fluid> SafeContext.internalSearchFluids(
        pos: FastVector,
        range: FastVector = MAGICVECTOR times 7,
        step: FastVector = MAGICVECTOR,
        pointer: MutableMap<FastVector, T>? = null,
        predicate: (FastVector, FluidState) -> Boolean = { _, _ -> true },
        iterator: (FastVector, FluidState) -> Unit = { _, _ -> },
    ) = internalSearchFluids(T::class, pos, range, step, pointer, predicate, iterator)

    @InternalApi
    inline fun <T : Fluid> SafeContext.internalSearchFluids(
        kClass: KClass<out T>,
        pos: FastVector,
        range: FastVector = MAGICVECTOR times 7,
        step: FastVector = MAGICVECTOR,
        pointer: MutableMap<FastVector, T>? = null,
        predicate: (FastVector, FluidState) -> Boolean = { _, _ -> true },
        iterator: (FastVector, FluidState) -> Unit = { _, _ -> },
    ) {
        internalIteratePositions(pos, range, step) { position ->
            world.getFluidState(position.x, position.y, position.z).let { state ->
                val fulfilled = kClass.isInstance(state.fluid) && predicate(position, state)

                if (fulfilled && pointer != null) {
                    pointer[position] = state.fluid as T

                    iterator(position, state)
                }
            }
        }
    }

    /**
     * Iterates over all positions within the specified range.
     * @param pos The position to start from.
     * @param range The maximum distance to search for entities in each axis.
     * @param step The step to increment the position by.
     * @param iterator Iterator to perform operations on each position.
     */
    @InternalApi
    inline fun internalIteratePositions(
        pos: FastVector,
        range: FastVector,
        step: FastVector,
        iterator: (FastVector) -> Unit = { _ -> },
    ) {
        for (x in -range.x..range.x step step.x) {
            for (y in -range.y..range.y step step.y) {
                for (z in -range.z..range.z step step.z) {
                    iterator(
                        pos plus fastVectorOf(x, y, z),
                    )
                }
            }
        }
    }
}

