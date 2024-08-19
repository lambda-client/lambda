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

/**
 * The `BlockDsl` class provides a DSL for performing block search operations
 * within a specified range in a Minecraft world. It allows for filtering and iterating blocks
 * around a given position.
 *
 * @param safeContext The context in which the block search is performed, providing safe access to world data.
 * @param pos The position from which to start the block search.
 *
 * ### Usage Example:
 *
 * ```kotlin
 * val blocks = blockSearch {
 *     range(10.0) // Search for blocks within a 10 block radius
 *     step(2.0) // Check for blocks every 2 block
 *     filter { _, block -> block.isOf(Blocks.DIAMOND_BLOCK) } // Filter out blocks that are not diamond blocks
 *     iterator { pos, state -> println("Found diamond block at: $pos") } // Print the position of each diamond block
 * }.build() // Finalize the block search
 *
 * println("Found ${blocks.size} diamond blocks.")
 * ```
 */
class BlockDsl(
    private val safeContext: SafeContext,
    pos: BlockPos,
) {
    private val fastVector = pos.toFastVec()
    private val receiver: MutableMap<FastVector, BlockState> = mutableMapOf()

    private var range: FastVector = MAGICVECTOR times 8
    private var step: FastVector = MAGICVECTOR
    private var predicate: (FastVector, BlockState) -> Boolean = { _, _ -> true }
    private var iterator: (FastVector, BlockState) -> Unit = { _, _ -> }

    /**
     * Sets the vector representing the maximum distances from the position to search for blocks.
     */
    fun range(range: Int): BlockDsl {
        this.range = fastVectorOf(range, range, range)
        return this
    }

    /**
     * Sets the vector representing the maximum distances from the position to search for blocks.
     */
    fun range(range: Double): BlockDsl {
        this.range = fastVectorOf(range.toInt(), range.toInt(), range.toInt())
        return this
    }

    /**
     * Sets the vector representing the maximum distances from the position to search for blocks.
     */
    fun range(range: Vec3i): BlockDsl {
        this.range = range.toFastVec()
        return this
    }

    /**
     * Sets the vector representing the maximum distances from the position to search for blocks.
     */
    fun range(range: Vec3d): BlockDsl {
        this.range = range.toFastVec()
        return this
    }

    /**
     * Sets the vector representing the intervals at which to check for blocks.
     */
    fun step(step: Int): BlockDsl {
        this.step = fastVectorOf(step, step, step)
        return this
    }

    /**
     * Sets the vector representing the intervals at which to check for blocks.
     */
    fun step(step: Double): BlockDsl {
        this.step = fastVectorOf(step.toInt(), step.toInt(), step.toInt())
        return this
    }

    /**
     * Sets the vector representing the intervals at which to check for blocks.
     */
    fun step(step: Vec3i): BlockDsl {
        this.step = step.toFastVec()
        return this
    }

    /**
     * Sets the vector representing the intervals at which to check for blocks.
     */
    fun step(step: Vec3d): BlockDsl {
        this.step = step.toFastVec()
        return this
    }

    /**
     * Sets a predicate to filter blocks.
     */
    fun filter(predicate: (BlockPos, BlockState) -> Boolean): BlockDsl {
        this.predicate = { pos, state -> predicate(pos.toBlockPos(), state) }
        return this
    }

    /**
     * Sets an iterator to perform operations on each block.
     */
    fun iterator(iterator: (BlockPos, BlockState) -> Unit): BlockDsl {
        this.iterator = { pos, state -> iterator(pos.toBlockPos(), state) }
        return this
    }

    fun build(): Map<BlockPos, BlockState> {
        safeContext.internalSearchBlocks(fastVector, range, step, receiver, predicate, iterator)
        return receiver.mapKeys { it.key.toBlockPos() }
    }
}

/**
 * Searches for blocks around the player's position and applies the specified block operations.
 * Don't forget to call [BlockDsl.build] to finalize the search.
 *
 * @param pos The position around which to search for blocks.
 * @param block The block operations to apply.
 */
fun SafeContext.blockSearch(pos: BlockPos = player.blockPos, block: BlockDsl.() -> Unit) = BlockDsl(this, pos).apply(block)

