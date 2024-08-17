package com.lambda.util.world

import com.lambda.context.SafeContext
import com.lambda.util.collections.filterPointer
import com.lambda.util.primitives.extension.getBlockState
import com.lambda.util.primitives.extension.getFluidState
import net.minecraft.block.BlockState
import net.minecraft.entity.Entity
import net.minecraft.fluid.Fluid
import net.minecraft.fluid.FluidState
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.Vec3d
import kotlin.math.ceil

/**
 * Utility functions for working with the Minecraft world.
 *
 * This object employs a pass-by-reference model, allowing functions to modify
 * data structures passed to them rather than creating new ones. This approach
 * offers 2 main benefits:
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
 * When it is no longer needed, the garbage collector frees up the memory.
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
     * Gets the closest entity of type [T] within a specified range.
     *
     *
     * @param pos The position to search from.
     * @param range The maximum distance to search for entities.
     * @param predicate Predicate to filter entities.
     * @return The first entity of type [T] that is closest to the position within the specified range.
     */
    inline fun <reified T : Entity> SafeContext.getClosestEntity(
        pos: Vec3d,
        range: Double,
        predicate: (T) -> Boolean = { true },
    ): T? {
        var closest: T? = null
        var closestDistance = Double.MAX_VALUE

        val comparator = { entity: T, _: Int ->
            val distance = pos.squaredDistanceTo(entity.pos)
            if (distance < closestDistance) {
                closest = entity
                closestDistance = distance
            }
        }

        // We use this function to find the closest entity because it is optimized for small distances.
        getFastEntities(pos, range, null, comparator, predicate)

        return closest
    }

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
     * For larger distances, it is recommended to use the [getEntities] function instead.
     * With the time complexity, we can determine that the performance of this function will degrade after 64 blocks.
     *
     * @param pos The position to search from.
     * @param distance The maximum distance to search for entities.
     * @param pointer The mutable list to store the entities in.
     * @param iterator Iterator to perform operations on each entity.
     * @param predicate Predicate to filter entities.
     */
    inline fun <reified T : Entity> SafeContext.getFastEntities(
        pos: Vec3d,
        distance: Double,
        pointer: MutableList<T>? = null,
        iterator: (T, Int) -> Unit = { _, _ -> },
        predicate: (T) -> Boolean = { true },
    ) {
        val chunks = ceil(distance / 16).toInt()
        val sectionX = pos.x.toInt() shr 4
        val sectionY = pos.y.toInt() shr 4
        val sectionZ = pos.z.toInt() shr 4

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

                    section.collection.filterPointer(pointer, iterator) { entity ->
                        entity != player &&
                                entity.squaredDistanceTo(pos) <= distance * distance &&
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
     * [getFastEntities], it traverses all entities in the world to find matches, while also excluding the player entity.
     *
     * @param pointer The mutable list to store the entities in.
     * @param iterator Iterator to perform operations on each entity.
     * @param predicate Predicate to filter entities.
     */
    inline fun <reified T : Entity> SafeContext.getEntities(
        pos: Vec3d,
        distance: Double,
        pointer: MutableList<T>? = null,
        iterator: (T, Int) -> Unit = { _, _ -> },
        predicate: (T) -> Boolean = { true },
    ) {
        world.entities.filterPointer(pointer, iterator) { entity ->
            entity != player &&
                    entity.squaredDistanceTo(pos) <= distance * distance &&
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
    inline fun SafeContext.searchBlocks(
        pos: FastVector,
        range: FastVector,
        step: FastVector = 274945015809L,
        pointer: MutableMap<FastVector, BlockState>? = null,
        predicate: (FastVector, BlockState, Int) -> Boolean = { _, _, _ -> true },
        iterator: (FastVector, BlockState, Int) -> Unit = { _, _, _ -> },
    ) {
        iteratePositions(pos, range, step) { position, index ->
            world.getBlockState(position.x, position.y, position.z).let { state ->
                val fulfilled = predicate(position, state, index)

                if (fulfilled && pointer != null) {
                    pointer[position] = state
                    iterator(position, state, index)
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
    inline fun <reified T : Fluid> SafeContext.searchFluids(
        pos: FastVector,
        range: FastVector,
        step: FastVector = 274945015809L,
        pointer: MutableMap<FastVector, T>? = null,
        predicate: (FastVector, FluidState, Int) -> Boolean = { _, _, _ -> true },
        iterator: (FastVector, FluidState, Int) -> Unit = { _, _, _ -> },
    ) {
        iteratePositions(pos, range, step) { position, index ->
            world.getFluidState(position.x, position.y, position.z).let { state ->
                val fulfilled = predicate(position, state, index)

                if (fulfilled && pointer != null) {
                    pointer[position] = state.fluid as T
                    iterator(position, state, index)
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
    inline fun iteratePositions(
        pos: FastVector,
        range: FastVector,
        step: FastVector = 274945015809L,
        iterator: (FastVector, Int) -> Unit = { _, _ -> },
    ) {
        var index = 0

        for (x in -range.x..range.x step step.x) {
            for (y in -range.y..range.y step step.y) {
                for (z in -range.z..range.z step step.z) {
                    iterator(
                        pos plus fastVectorOf(x, y, z),
                        index++
                    )
                }
            }
        }
    }
}

