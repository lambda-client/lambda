
package com.minato.util.world

import com.minato.context.SafeContext
import com.minato.util.math.distSq
import com.minato.util.world.WorldUtils.internalGetBlockEntities
import com.minato.util.world.WorldUtils.internalGetEntities
import com.minato.util.world.WorldUtils.internalGetFastEntities
import com.minato.util.world.WorldUtils.internalSearchBlocks
import com.minato.util.world.WorldUtils.internalSearchFluids
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.Entity
import net.minecraft.fluid.Fluid
import net.minecraft.fluid.FluidState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i

@DslMarker
annotation class BlockMarker

/**
 * Example:
 * ```
 * val blocks = blockSearch(range = Vec3i(10, 10, 10)) {
 *     it.isOf(Blocks.DIAMOND_BLOCK) // Filter out blocks that are not diamond blocks
 * }
 *
 * blocks.forEach { (pos, state) ->
 *     println("Found diamond block at: $pos")
 * }
 * ```
 */
@BlockMarker
fun SafeContext.blockSearch(
    range: Vec3i,
    pos: BlockPos = player.blockPos,
    step: Vec3i = Vec3i(1, 1, 1),
    filter: (BlockPos, BlockState) -> Boolean = { _, _ -> true },
) =
    internalSearchBlocks(
        pos.toFastVec(),
        range.toFastVec(),
        step.toFastVec()
    ) { fastPos, state ->
        filter(fastPos.toBlockPos(), state)
    }.mapKeys { it.key.toBlockPos() }

/**
 * Example:
 * ```
 * val blocks = blockSearch(range = Vec3i(10, 10, 10)) {
 *     it.isOf(Blocks.DIAMOND_BLOCK) // Filter out blocks that are not diamond blocks
 * }
 *
 * blocks.forEach { (pos, state) ->
 *     println("Found diamond block at: $pos")
 * }
 * ```
 */
@BlockMarker
fun SafeContext.blockSearch(
    range: Int,
    pos: BlockPos = player.blockPos,
    step: Int = 1,
    filter: (BlockPos, BlockState) -> Boolean = { _, _ -> true },
) = blockSearch(Vec3i(range, range, range), pos, Vec3i(step, step, step), filter)

@DslMarker
annotation class BlockEntityMarker

/**
 * Example
 * ```
 * val blockEntities = blockEntitySearch<ShulkerBoxEntity>(range = 10.0) {
 *     !it.isRemoved // Filter out existing block entities
 * }
 * ```
 */
@BlockEntityMarker
inline fun <reified T : BlockEntity> SafeContext.blockEntitySearch(
    range: Double = 64.0,
    pos: BlockPos = player.blockPos,
    noinline filter: (T) -> Boolean = { true },
) = internalGetBlockEntities<T>(pos.toFastVec(), range, predicate = filter).toSet()

@DslMarker
annotation class EntityMarker

/**
 * Example:
 * ```kotlin
 * val closestPlayer = entitySearch<PlayerEntity>(range = 20.0) {
 *     it.isAlive // Filter out dead entities
 * }
 * ```
 */
@EntityMarker
inline fun <reified T : Entity> SafeContext.closestEntity(
    range: Double = 64.0,
    pos: BlockPos = player.blockPos,
    noinline filter: (T) -> Boolean = { true },
): T? =
    entitySearch<T>(range, pos, filter)
        .minByOrNull { pos distSq it.pos }

/**
 * Example:
 * ```kotlin
 * val entities = entitySearch<LivingEntity>(range = 20.0) {
 *     it.isAlive // Filter out dead entities
 * }
 * ```
 */
@EntityMarker
inline fun <reified T : Entity> SafeContext.entitySearch(
    range: Double,
    pos: BlockPos = player.blockPos,
    noinline filter: (T) -> Boolean = { true },
) = internalGetEntities<T>(pos.toFastVec(), range, filter = filter)

/**
 * Example:
 * ```
 * val entities = fastEntitySearch<LivingEntity>(range = 10.0) {
 *     it.isAlive && // Filter out dead entities
 *         it.isGlowing // Filter out entities that are not glowing
 * }
 * ```
 */
@EntityMarker
inline fun <reified T : Entity> SafeContext.fastEntitySearch(
    range: Double,
    pos: BlockPos = player.blockPos,
    noinline filter: (T) -> Boolean = { true },
) = internalGetFastEntities<T>(pos.toFastVec(), range, filter = filter)

@DslMarker
annotation class FluidMarker

/**
 * Example:
 * ```kotlin
 * val fluids = fluidSearch<LavaFluid.Still>(range = Vec3i(8.0, 3.0, 8.0)) { // Search for fluids within a box of (8, 3, 8)
 *     it.isOf(Fluids.LAVA) // Filter out fluids that are not lava
 * }
 *
 * fluids.forEach { (pos, state) ->
 *     println("Found still lava at $pos with state $state")
 * }
 * ```
 */
@FluidMarker
inline fun <reified T : Fluid> SafeContext.fluidSearch(
    range: Vec3i,
    pos: BlockPos = player.blockPos,
    step: Vec3i = Vec3i(1, 1, 1),
    noinline filter: (BlockPos, FluidState) -> Boolean = { _, _ -> true },
) =
    internalSearchFluids<T>(
        pos.toFastVec(),
        range.toFastVec(),
        step.toFastVec()
    ) { pos, state -> filter(pos.toBlockPos(), state) }
        .mapKeys { it.key.toBlockPos() }

/**
 * Example:
 * ```kotlin
 * val fluids = fluidSearch<LavaFluid.Still>(range = 8.0) { // Search for fluids in a range of 8 blocks
 *     it.isOf(Fluids.LAVA) // Filter out fluids that are not lava
 * }
 *
 * fluids.forEach { (pos, state) ->
 *     println("Found still lava at $pos with state $state")
 * }
 * ```
 */
@FluidMarker
inline fun <reified T : Fluid> SafeContext.fluidSearch(
    range: Int,
    step: Int = 1,
    pos: BlockPos = player.blockPos,
    noinline filter: (BlockPos, FluidState) -> Boolean,
) = fluidSearch<T>(Vec3i(range, range, range), pos, Vec3i(step, step, step), filter)
