package com.lambda.util.world

import com.lambda.context.SafeContext
import com.lambda.util.world.WorldUtils.getClosestEntity
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

@WorldDsl
class SearchContext(val safeContext: SafeContext, val pos: Vec3d) {
    /**
     * Get the closest entity to the specified position.
     * @param range The range to search in.
     * @param predicate The predicate to filter the entities.
     */
    inline fun <reified T : Entity> closestEntity(
        range: Double,
        predicate: (T) -> Boolean = { true },
    ): T? = safeContext.getClosestEntity(pos, range, predicate)

    /**
     * Get all entities in the specified range.
     * @param range The range to search in.
     * @param predicate The predicate to filter the entities.
     * @return A list of entities found.
     */
    inline fun <reified T : Entity> entities(
        range: Double,
        predicate: (T) -> Boolean = { true },
    ): List<T> {
        val entities = mutableListOf<T>()
        safeContext.getFastEntities(pos, range, entities, predicate = predicate)
        return entities
    }

    /**
     * Search for blocks in the specified range.
     * @param range The range to search in.
     * @param step The step to search with.
     * @param predicate The predicate to filter the blocks.
     * @return A map of the blocks found to positions.
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
     * Search for blocks in the specified range.
     * @param range The range to search in.
     * @param step The step to search with.
     * @param predicate The predicate to filter the blocks.
     * @return A map of the blocks found to positions.
     */
    inline fun blocks(
        range: Vec3d,
        step: Vec3i = Vec3i(1, 1, 1),
        crossinline predicate: (Vec3d, BlockState) -> Boolean = { _, _ -> true }
    ): Map<Vec3d, BlockState> = blocks(range, Vec3d(step.x.toDouble(), step.y.toDouble(), step.z.toDouble()), predicate)

    /**
     * Search for blocks in the specified range.
     * @param range The range to search in.
     * @param step The step to search with.
     * @param predicate The predicate to filter the blocks.
     * @param iterator The iterator to call for each block found.
     * @return A map of the blocks found to positions.
     */
    inline fun blocks(
        range: Vec3d,
        step: Vec3d = Vec3d(1.0, 1.0, 1.0),
        crossinline predicate: (Vec3d, BlockState) -> Boolean = { _, _ -> true },
        crossinline iterator: (Vec3d, BlockState) -> Unit = { _, _ -> },
    ): Map<Vec3d, BlockState> {
        val blocks = mutableMapOf<FastVector, BlockState>()

        val transformedPredicate: (FastVector, BlockState, Int) -> Boolean =
            { fast, state, _ -> predicate(fast.decoded(), state) }
        val transformedIterator: (FastVector, BlockState, Int) -> Unit =
            { fast, state, _ -> iterator(fast.decoded(), state) }

        safeContext.searchBlocks(pos.encoded(), range.encoded(), step.encoded(), blocks, transformedPredicate, transformedIterator)
        return blocks.mapKeys { it.key.decoded() }
    }

    /**
     * Search for fluids in the specified range.
     * @param range The range to search in.
     * @param step The step to search with.
     * @param predicate The predicate to filter the fluids.
     * @return A map of the fluids found to positions.
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
     * Search for fluids in the specified range.
     * @param range The range to search in.
     * @param step The step to search with.
     * @param predicate The predicate to filter the fluids.
     * @return A map of the fluids found to positions.
     */
    inline fun fluids(
        range: Vec3d,
        step: Vec3i = Vec3i(1, 1, 1),
        crossinline predicate: (Vec3d, FluidState) -> Boolean = { _, _ -> true },
    ): Map<Vec3d, Fluid> = fluids(range, Vec3d(step.x.toDouble(), step.y.toDouble(), step.z.toDouble()), predicate)

    /**
     * Search for fluids in the specified range.
     * @param range The range to search in.
     * @param step The step to search with.
     * @param predicate The predicate to filter the fluids.
     * @param iterator The iterator to call for each fluid found.
     * @return A map of the fluids found to positions.
     */
    inline fun <reified T : Fluid> fluids(
        range: Vec3d,
        step: Vec3d = Vec3d(1.0, 1.0, 1.0),
        crossinline predicate: (Vec3d, FluidState) -> Boolean = { _, _ -> true },
        crossinline iterator: (Vec3d, FluidState) -> Unit = { _, _ -> },
    ): Map<Vec3d, T> {
        val fluids = mutableMapOf<FastVector, T>()

        val transformedPredicate: (FastVector, FluidState, Int) -> Boolean =
            { fast, state, _ -> predicate(fast.decoded(), state) }
        val transformedIterator: (FastVector, FluidState, Int) -> Unit =
            { fast, state, _ -> iterator(fast.decoded(), state) }

        safeContext.searchFluids(pos.encoded(), range.encoded(), step.encoded(), fluids, transformedPredicate, transformedIterator)
        return fluids.mapKeys { it.key.decoded() }
    }
}

fun SafeContext.search(pos: Vec3d = player.pos, block: SearchContext.() -> Unit) =
    SearchContext(this, pos).apply(block)
