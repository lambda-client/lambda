@file:OptIn(InternalApi::class)

package com.lambda.util.world

import com.lambda.context.SafeContext
import com.lambda.core.annotations.InternalApi
import com.lambda.util.world.WorldUtils.MAGICVECTOR
import com.lambda.util.world.WorldUtils.internalSearchFluids
import net.minecraft.fluid.Fluid
import net.minecraft.fluid.FluidState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import kotlin.reflect.KClass

/**
 * The `FluidDsl` class provides a DSL for performing fluid search operations
 * within a specified range in a Minecraft world. It allows for filtering and iterating fluids
 * of a specific type [T] around a given position.
 *
 * @param safeContext The context in which the fluid search is performed, providing safe access to world data.
 * @param kClass The class of the fluid type [T].
 * @param pos The position from which to start the fluid search.
 *
 * ### Usage Example:
 *
 * ```kotlin
 * val fluids = fluidSearch<LavaFluid.Still> {
 *     range(8) // Search for fluids within an 8 block radius
 *     iterator { pos, state -> println("Found immobile lava at $pos with state $state") }
 * }.build() // Finalize the fluid search
 *
 * println("Found ${fluids.size} immobile lava fluids.")
 * ```
 */
class FluidDsl<T : Fluid>(
    private val safeContext: SafeContext,
    private val kClass: KClass<out T>,
    pos: BlockPos,
) {
    private val fastVector = pos.toFastVec()
    private val receiver: MutableMap<FastVector, T> = mutableMapOf()

    private var range: FastVector = MAGICVECTOR times 8
    private var step: FastVector = MAGICVECTOR
    private var predicate: (FastVector, FluidState) -> Boolean = { _, _ -> true }
    private var iterator: (FastVector, FluidState) -> Unit = { _, _ -> }

    /**
     * Sets the range around the position to search for fluids.
     */
    fun range(range: Double): FluidDsl<T> {
        this.range = fastVectorOf(range.toInt(), range.toInt(), range.toInt())
        return this
    }

    /**
     * Sets the range around the position to search for fluids.
     */
    fun range(range: Int): FluidDsl<T> {
        this.range = fastVectorOf(range, range, range)
        return this
    }

    /**
     * Sets the range around the position to search for fluids.
     */
    fun range(range: Vec3i): FluidDsl<T> {
        this.range = range.toFastVec()
        return this
    }

    /**
     * Sets the range around the position to search for fluids.
     */
    fun range(range: Vec3d): FluidDsl<T> {
        this.range = range.toFastVec()
        return this
    }

    /**
     * Sets the vector representing the intervals at which to check for blocks.
     */
    fun step(step: Int): FluidDsl<T> {
        this.step = fastVectorOf(step, step, step)
        return this
    }

    /**
     * Sets the vector representing the intervals at which to check for blocks.
     */
    fun step(step: Double): FluidDsl<T> {
        this.step = fastVectorOf(step.toInt(), step.toInt(), step.toInt())
        return this
    }

    /**
     * Sets the vector representing the intervals at which to check for blocks.
     */
    fun step(step: Vec3i): FluidDsl<T> {
        this.step = step.toFastVec()
        return this
    }

    /**
     * Sets the vector representing the intervals at which to check for blocks.
     */
    fun step(step: Vec3d): FluidDsl<T> {
        this.step = step.toFastVec()
        return this
    }

    /**
     * Sets a predicate to filter fluids.
     */
    fun filter(predicate: (BlockPos, FluidState) -> Boolean): FluidDsl<T> {
        this.predicate = { pos, state -> predicate(pos.toBlockPos(), state) }
        return this
    }

    /**
     * Sets an iterator to perform operations on each fluid.
     */
    fun iterator(iterator: (BlockPos, FluidState) -> Unit): FluidDsl<T> {
        this.iterator = { pos, state -> iterator(pos.toBlockPos(), state) }
        return this
    }

    /**
     * Builds the map of fluids found in the world.
     */
    fun build(): Map<BlockPos, T> {
        safeContext.internalSearchFluids(kClass, fastVector, range, step, receiver, predicate, iterator)

        return receiver.mapKeys { it.key.toBlockPos() }
    }
}

/**
 * Initiates a fluid search operation in the world at the specified position using a [FluidDsl].
 * The fluid search operation is performed using the specified block of code.
 *
 * @param pos The position to start the search from. Defaults to the player's current position.
 * @param block The block of code that performs the search using the [FluidDsl].
 */
inline fun <reified T : Fluid> SafeContext.fluidSearch(pos: BlockPos = player.blockPos, block: FluidDsl<T>.() -> Unit) = FluidDsl(this, T::class, pos).apply(block)
