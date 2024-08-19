@file:OptIn(InternalApi::class)

package com.lambda.util.world

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.core.annotations.InternalApi
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.world.WorldUtils.getEntities
import com.lambda.util.world.WorldUtils.getFastEntities
import com.lambda.util.world.WorldUtils.searchBlocks
import com.lambda.util.world.WorldUtils.searchFluids
import net.minecraft.block.BlockState
import net.minecraft.entity.Entity
import net.minecraft.fluid.Fluid
import net.minecraft.fluid.FluidState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i

@DslMarker
annotation class WorldDsl

/**
 * A context for performing various high-level search operations within the world.
 * These functions prioritize ease of use and flexibility, potentially at the cost
 * of performance.
 *
 * @property safeContext The context that ensures safe operations within the world.
 * @property pos The position from which searches are conducted.
 */
@WorldDsl
class SearchContext(val safeContext: SafeContext, val pos: BlockPos) {
    val fastVector: FastVector = pos.toFastVec()

    /**
     * Finds the closest entity of type [T] to the specified position within a given range.
     *
     * @param range The maximum distance from the position to search for entities.
     * @param predicate A filter to determine which entities to include in the search.
     * @return The closest entity of type [T] that matches the predicate, or null if no entity is found.
     */
    inline fun <reified T : Entity> closestEntity(
        range: Double,
        predicate: (T) -> Boolean = { true },
    ): T? = minEntityBy(range, predicate) { entity -> pos distSq entity.pos }

    /**
     * Returns the entity that has the smallest value based on the given comparator.
     * If multiple entities have the same value, the first one found will be returned.
     *
     * @param range The maximum distance from the position to search for entities.
     * @param comparator A function that compares two entities and returns a value.
     * @param predicate A filter to determine which entities to include in the search.
     * @return The entity with the smallest value based on the comparator, or null if no entity is found.
     */
    inline fun <reified T : Entity> minEntityBy(
        range: Double,
        predicate: (T) -> Boolean = { true },
        comparator: SafeContext.(T) -> Double,
    ): T? {
        var min: T? = null
        var minValue = Double.MAX_VALUE

        safeContext.getFastEntities(fastVector, range, predicate = predicate) { entity, _ ->
            val value = comparator(safeContext, entity)

            if (value < minValue) {
                min = entity
                minValue = value
            }
        }

        return min
    }

    /**
     * Retrieves a list of nearby entities of type [T] within the specified range.
     *
     * @param range The radius around the position to search for entities.
     * @param predicate A filter to determine which entities to include in the search.
     * @return A list of entities of type [T]
     */
    inline fun <reified T : Entity> nearbyEntities(
        range: Double,
        predicate: (T) -> Boolean = { true },
        iterator: (T) -> Unit = { _ -> },
    ): List<T> {
        if (range >= 64) {
            LOG.warn("Searching nearby entities with a range of $range may be slow. " +
                    "Consider reducing the range to improve performance or using a linear search instead.")
        }

        val entities = mutableListOf<T>()

        safeContext.getFastEntities(fastVector, range, entities, predicate) { entity, _ -> iterator(entity) }

        return entities
    }

    /**
     * Retrieves a list of entities of type [T] within the specified range.
     *
     * @param range The radius around the position to search for entities.
     * @param predicate A filter to determine which entities to include in the search.
     * @return A list of entities of type [T].
     */
    inline fun <reified T : Entity> entities(
        range: Int,
        predicate: (T) -> Boolean = { true },
        iterator: (T) -> Unit = { _ -> },
    ): List<T> {
        val entities = mutableListOf<T>()

        safeContext.getEntities(fastVector, range.toDouble(), entities, predicate) { entity, _ -> iterator(entity) }

        return entities
    }

    /**
     * Searches for blocks within the specified range and step size.
     *
     * @param range The maximum distance from the position to search for blocks.
     * @param step The interval at which to check for blocks. Defaults to 1.
     * @param predicate A filter to determine which blocks to include in the search. Defaults to always true.
     * @return A map of positions to block states.
     */
    inline fun blocks(
        range: Int,
        step: Int = 1,
        crossinline predicate: (BlockPos, BlockState) -> Boolean = { _, _ -> true },
        crossinline iterator: (BlockPos, BlockState) -> Unit = { _, _ -> },
    ): Map<BlockPos, BlockState> = blocks(
        BlockPos(range, range, range),
        BlockPos(step, step, step),
        predicate, iterator,
    )

    /**
     * Searches for blocks within the specified 3D range and step size, with an optional iterator function.
     *
     * @param range The 3D vector representing the maximum distances from the position to search for blocks.
     * @param step The 3D vector representing the intervals at which to check for blocks.
     * @param predicate A filter to determine which blocks to include in the search.
     * @param iterator A function to be called for each block found, allowing additional processing
     * @return A map of positions to block states.
     */
    inline fun blocks(
        range: Vec3i,
        step: Vec3i = Vec3i(1, 1, 1),
        crossinline predicate: (BlockPos, BlockState) -> Boolean = { _, _ -> true },
        crossinline iterator: (BlockPos, BlockState) -> Unit = { _, _ -> },
    ): Map<BlockPos, BlockState> {
        val blocks = mutableMapOf<FastVector, BlockState>()

        val transformedPredicate: (FastVector, BlockState, Int) -> Boolean =
            { fast, state, _ -> predicate(fast.toBlockPos(), state) }
        val transformedIterator: (FastVector, BlockState, Int) -> Unit =
            { fast, state, _ -> iterator(fast.toBlockPos(), state) }

        safeContext.searchBlocks(fastVector, range.toFastVec(), step.toFastVec(), blocks, transformedPredicate, transformedIterator)

        return blocks.mapKeys { it.key.toBlockPos() }
    }

    /**
     * Searches for fluids within the specified range and step size.
     *
     * @param range The maximum distance from the position to search for fluids.
     * @param step The interval at which to check for fluids.
     * @param predicate A filter to determine which fluids to include in the search.
     * @return A map of positions to fluids.
     */
    inline fun fluids(
        range: Int,
        step: Int = 1,
        crossinline predicate: (BlockPos, FluidState) -> Boolean = { _, _ -> true },
        crossinline iterator: (BlockPos, FluidState) -> Unit = { _, _ -> },
    ): Map<BlockPos, Fluid> = fluids(
        BlockPos(range, range, range),
        BlockPos(step, step, step),
        predicate, iterator,
    )

    /**
     * Searches for fluids within the specified 3D range and step size, with an optional iterator function.
     *
     * @param range The vector representing the maximum distances from the position to search for fluids.
     * @param step The vector representing the intervals at which to check for fluids.
     * @param predicate A filter to determine which fluids to include in the search.
     * @param iterator A function to be called for each fluid found, allowing additional processing.
     * @return A map of positions to fluids.
     */
    inline fun <reified T : Fluid> fluids(
        range: Vec3i,
        step: Vec3i = Vec3i(1, 1, 1),
        crossinline predicate: (BlockPos, FluidState) -> Boolean = { _, _ -> true },
        crossinline iterator: (BlockPos, FluidState) -> Unit = { _, _ -> },
    ): Map<BlockPos, T> {
        val fluids = mutableMapOf<FastVector, T>()

        val transformedPredicate: (FastVector, FluidState, Int) -> Boolean =
            { fast, state, _ -> predicate(fast.toBlockPos(), state) }
        val transformedIterator: (FastVector, FluidState, Int) -> Unit =
            { fast, state, _ -> iterator(fast.toBlockPos(), state) }

        safeContext.searchFluids(pos.toFastVec(), range.toFastVec(), step.toFastVec(), fluids, transformedPredicate, transformedIterator)
        return fluids.mapKeys { it.key.toBlockPos() }
    }
}

/**
 * Initiates a search operation in the world at the specified position using a [SearchContext].
 *
 * @param pos The position to start the search from. Defaults to the player's current position.
 * @param block The block of code that performs the search using the [SearchContext].
 */
inline fun SafeContext.search(pos: BlockPos = player.blockPos, block: SearchContext.() -> Unit) =
    SearchContext(this, pos).apply(block)
