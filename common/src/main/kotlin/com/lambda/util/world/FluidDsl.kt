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

@file:OptIn(InternalApi::class)

package com.lambda.util.world

import com.lambda.context.SafeContext
import com.lambda.core.annotations.InternalApi
import com.lambda.util.world.WorldUtils.internalSearchFluids
import net.minecraft.fluid.Fluid
import net.minecraft.fluid.FluidState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@DslMarker
annotation class FluidDslMarker

/**
 * The [FluidDsl] class provides a DSL for performing fluid search operations
 * within a specified range in a Minecraft world. It allows for filtering fluids
 * around a given position.
 *
 * @param safeContext The context in which the fluid search is performed, providing safe access to world data.
 * @param kClass The class of the fluid type [T].
 * @param pos The position from which to start the fluid search.
 * @param range The range around the position to search for fluids.
 * @param step The step intervals at which to check for fluids.
 * @param predicate The predicate to filter fluids.
 *
 * ### Usage Example:
 *
 * ```kotlin
 * val fluids = fluidSearch<LavaFluid.Still>(range = 8) { // Search for fluids within an 8 block radius
 *     it.isOf(Fluids.LAVA) // Filter out fluids that are not lava
 * }
 *
 * fluids.forEach { (pos, state) ->
 *     println("Found still lava at $pos with state $state")
 * }
 * ```
 */
@FluidDslMarker
class FluidDsl<T : Fluid>(
    private val safeContext: SafeContext,
    private val kClass: KClass<out T>,
    pos: BlockPos,
    private val range: Vec3i,
    private val step: Vec3i,
    private val predicate: (BlockPos, FluidState) -> Boolean
) {
    private val fastVector = pos.toFastVec()
    private val receiver: MutableMap<FastVector, T> = mutableMapOf()

    fun build(): Map<BlockPos, T> {
        safeContext.internalSearchFluids(
            kClass,
            fastVector,
            range.toFastVec(),
            step.toFastVec(),
            receiver,
            { pos, state -> predicate(pos.toBlockPos(), state) }
        )

        return receiver.mapKeys { it.key.toBlockPos() }
    }
}

/**
 * Searches for fluids around the player's position and applies the specified fluid operations.
 *
 * @param pos The position around which to search for fluids. Defaults to the player's current position.
 * @param range The `x`, `y`, `z` range around the position to search for fluids.
 * @param step The `x`, `y`, `z` step intervals at which to check for fluids.
 * @param predicate The predicate to filter fluids.
 * @return A map of fluid positions and their states matching the predicate within the specified range.
 */
inline fun <reified T : Fluid> SafeContext.fluidSearch(
    range: Vec3i,
    step: Vec3i,
    pos: BlockPos = player.blockPos,
    noinline predicate: (BlockPos, FluidState) -> Boolean
): Map<BlockPos, T> = FluidDsl(this, T::class, pos, range, step, predicate).build()

/**
 * Searches for fluids around the player's position and applies the specified fluid operations.
 *
 * @param pos The position around which to search for fluids. Defaults to the player's current position.
 * @param range The range around the position to search for fluids.
 * @param step The step intervals at which to check for fluids.
 * @param predicate The predicate to filter fluids.
 * @return A map of fluid positions and their states matching the predicate within the specified range.
 */
inline fun <reified T : Fluid> SafeContext.fluidSearch(
    range: Int,
    step: Int,
    pos: BlockPos = player.blockPos,
    noinline predicate: (BlockPos, FluidState) -> Boolean
): Map<BlockPos, T> = fluidSearch(Vec3i(range, range, range), Vec3i(step, step, step), pos, predicate)
