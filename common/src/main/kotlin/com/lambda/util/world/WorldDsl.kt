package com.lambda.util.world

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.util.world.WorldUtils.getClosestEntity
import com.lambda.util.world.WorldUtils.getEntities
import com.lambda.util.world.WorldUtils.getFastEntities
import com.lambda.util.world.WorldUtils.searchBlocks
import com.lambda.util.world.WorldUtils.searchFluids
import net.minecraft.block.BlockState
import net.minecraft.entity.Entity
import net.minecraft.fluid.Fluid
import net.minecraft.fluid.FluidState
import net.minecraft.util.math.Vec3d
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
class SearchContext(val safeContext: SafeContext, val pos: Vec3d) {
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
    ): T? = safeContext.getClosestEntity(pos, range, predicate)

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
    ): List<T> {
        val entities = mutableListOf<T>()

        if (range >= 64) {
            LOG.warn("Searching nearby entities with a range of $range may be slow. " +
                    "Consider reducing the range to improve performance or using a linear search instead.")
        }

        safeContext.getFastEntities(pos, range, entities, predicate = predicate)

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
        range: Double,
        predicate: (T) -> Boolean = { true },
    ): List<T> {
        val entities = mutableListOf<T>()

        safeContext.getEntities(pos, range, entities, predicate = predicate)

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
        crossinline predicate: (Vec3d, BlockState) -> Boolean = { _, _ -> true }
    ): Map<Vec3d, BlockState> = blocks(
        Vec3d(range.toDouble(), range.toDouble(), range.toDouble()),
        Vec3d(step.toDouble(), step.toDouble(), step.toDouble()),
        predicate
    )

    /**
     * Searches for blocks within the specified 3D range and step size.
     *
     * @param range The vector representing the maximum distances from the position to search for blocks.
     * @param step The vector representing the intervals at which to check for blocks.
     * @param predicate A filter to determine which blocks to include in the search.
     * @return A map of positions to block states.
     */
    inline fun blocks(
        range: Vec3d,
        step: Vec3i = Vec3i(1, 1, 1),
        crossinline predicate: (Vec3d, BlockState) -> Boolean = { _, _ -> true }
    ): Map<Vec3d, BlockState> = blocks(
        range,
        Vec3d(step.x.toDouble(), step.y.toDouble(), step.z.toDouble()),
        predicate
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
        range: Vec3d,
        step: Vec3d = Vec3d(1.0, 1.0, 1.0),
        crossinline predicate: (Vec3d, BlockState) -> Boolean = { _, _ -> true },
        crossinline iterator: (Vec3d, BlockState) -> Unit = { _, _ -> },
    ): Map<Vec3d, BlockState> {
        val blocks = mutableMapOf<FastVector, BlockState>()

        val transformedPredicate: (FastVector, BlockState, Int) -> Boolean =
            { fast, state, _ -> predicate(fast.toVec3d(), state) }
        val transformedIterator: (FastVector, BlockState, Int) -> Unit =
            { fast, state, _ -> iterator(fast.toVec3d(), state) }

        safeContext.searchBlocks(pos.toFastVec(), range.toFastVec(), step.toFastVec(), blocks, transformedPredicate, transformedIterator)
        return blocks.mapKeys { it.key.toVec3d() }
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
        crossinline predicate: (Vec3d, FluidState) -> Boolean = { _, _ -> true },
    ): Map<Vec3d, Fluid> = fluids(
        Vec3d(range.toDouble(), range.toDouble(), range.toDouble()),
        Vec3d(step.toDouble(), step.toDouble(), step.toDouble()),
        predicate
    )

    /**
     * Searches for fluids within the specified 3D range and step size.
     *
     * @param range The vector representing the maximum distances from the position to search for fluids.
     * @param step The vector representing the intervals at which to check for fluids. Defaults to (1, 1, 1).
     * @param predicate A filter to determine which fluids to include in the search.
     * @return A map of positions to fluids.
     */
    inline fun fluids(
        range: Vec3d,
        step: Vec3i = Vec3i(1, 1, 1),
        crossinline predicate: (Vec3d, FluidState) -> Boolean = { _, _ -> true },
    ): Map<Vec3d, Fluid> = fluids(
        range,
        Vec3d(step.x.toDouble(), step.y.toDouble(), step.z.toDouble()),
        predicate
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
        range: Vec3d,
        step: Vec3d = Vec3d(1.0, 1.0, 1.0),
        crossinline predicate: (Vec3d, FluidState) -> Boolean = { _, _ -> true },
        crossinline iterator: (Vec3d, FluidState) -> Unit = { _, _ -> },
    ): Map<Vec3d, T> {
        val fluids = mutableMapOf<FastVector, T>()

        val transformedPredicate: (FastVector, FluidState, Int) -> Boolean =
            { fast, state, _ -> predicate(fast.toVec3d(), state) }
        val transformedIterator: (FastVector, FluidState, Int) -> Unit =
            { fast, state, _ -> iterator(fast.toVec3d(), state) }

        safeContext.searchFluids(pos.toFastVec(), range.toFastVec(), step.toFastVec(), fluids, transformedPredicate, transformedIterator)
        return fluids.mapKeys { it.key.toVec3d() }
    }
}

/**
 * Initiates a search operation in the world at the specified position using a [SearchContext].
 *
 * @param pos The position to start the search from. Defaults to the player's current position.
 * @param block The block of code that performs the search using the [SearchContext].
 */
fun SafeContext.search(pos: Vec3d = player.pos, block: SearchContext.() -> Unit) =
    SearchContext(this, pos).apply(block)
