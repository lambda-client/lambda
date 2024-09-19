@file:OptIn(InternalApi::class)

package com.lambda.util.world

import com.lambda.context.SafeContext
import com.lambda.core.annotations.InternalApi
import com.lambda.util.world.WorldUtils.MAGICVECTOR
import com.lambda.util.world.WorldUtils.internalSearchBlocks
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i

@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@DslMarker
annotation class BlockDslMarker

/**
 * The [BlockDsl] class provides a DSL for performing block search operations
 * within a specified range in a Minecraft world.
 * It allows for filtering blocks around a given position.
 *
 * @param safeContext The context in which the block search is performed, providing safe access to world data.
 * @param pos The position from which to start the block search.
 *
 * ### Usage Example:
 *
 * ```kotlin
 * val blocks = blockSearch(range = Vec3i(10, 10, 10)) {
 *     it.isOf(Blocks.DIAMOND_BLOCK) // Filter out blocks that are not diamond blocks
 * }
 *
 * blocks.forEach { (pos, state) ->
 *     println("Found diamond block at: $pos")
 * }
 * ```
 */
@BlockDslMarker
class BlockDsl(
    private val safeContext: SafeContext,
    pos: BlockPos,
    private val range: Vec3i,
    private val step: Vec3i,
    private val predicate: (BlockPos, BlockState) -> Boolean
) {
    private val fastVector = pos.toFastVec()
    private val receiver: MutableMap<FastVector, BlockState> = mutableMapOf()

    fun build(): Map<BlockPos, BlockState> {
        safeContext.internalSearchBlocks(fastVector, range.toFastVec(), step.toFastVec(), receiver, { pos, state -> predicate(pos.toBlockPos(), state) }, { _, _ -> })
        return receiver.mapKeys { it.key.toBlockPos() }
    }
}

/**
 * Searches for blocks around the player's position and applies the specified block operations.
 *
 * @param pos The position around which to search for blocks. Defaults to the player's current position.
 * @param range The `x`, `y`, `z` range around the position to search for blocks.
 * @param step The `x`, `y`, `z` step intervals at which to check for blocks.
 * @param predicate The predicate to filter blocks.
 * @return A map of block positions and their states matching the predicate within the specified range.
 */
fun SafeContext.blockSearch(
    range: Vec3i,
    step: Vec3i,
    pos: BlockPos = player.blockPos,
    predicate: (BlockPos, BlockState) -> Boolean
): Map<BlockPos, BlockState> =
    BlockDsl(this, pos, range, step, predicate).build()

/**
 * Searches for blocks around the player's position and applies the specified block operations.
 *
 * @param pos The position around which to search for blocks. Defaults to the player's current position.
 * @param range The range around the position to search for blocks.
 * @param step The step intervals at which to check for blocks.
 * @param predicate The predicate to filter blocks.
 * @return A map of block positions and their states matching the predicate within the specified range.
 */
fun SafeContext.blockSearch(
    range: Int,
    step: Int,
    pos: BlockPos = player.blockPos,
    predicate: (BlockPos, BlockState) -> Boolean
): Map<BlockPos, BlockState> =
    blockSearch(Vec3i(range, range, range), Vec3i(step, step, step), pos, predicate)