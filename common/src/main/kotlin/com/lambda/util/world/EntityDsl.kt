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
import com.lambda.util.world.WorldUtils.internalGetEntities
import com.lambda.util.world.WorldUtils.internalGetFastEntities
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import kotlin.reflect.KClass

@DslMarker
annotation class EntityDslMarker

/**
 * The [EntityDsl] class provides a DSL for performing entity search operations
 * within a specified range in a Minecraft world. It allows for filtering entities
 * of a specific type [T] around a given position.
 *
 * @param safeContext The context in which the entity search is performed, providing safe access to world data.
 * @param kClass The class of the entity type [T].
 * @param pos The position from which to start the entity search.
 * @param range The range around the position to search for entities.
 * @param predicate The predicate to filter entities.
 *
 * ### Usage Example:
 *
 * ```kotlin
 * val entities = entitySearch<LivingEntity>(range = 10.0) {
 *     it.isAlive // Filter out dead entities
 * }
 *
 * val closestEntityToFOV = entities.minBy {
 *     player.rotation dist player.eyePos.rotationTo(it.pos)
 * }
 * ```
 */
@EntityDslMarker
class EntityDsl<T : Entity>(
    private val safeContext: SafeContext,
    private val kClass: KClass<out T>,
    pos: BlockPos,
    private val range: Double,
    private val predicate: (T) -> Boolean,
) {
    private val fastVector = pos.toFastVec()
    private val receiver: MutableList<T> = mutableListOf()

    /**
     * Retrieves a list of entities of type [T] within the specified range.
     * This method is safe to use with mutable lists and should be used when the optimized method is not suitable.
     */
    @EntityDslMarker
    fun build(): List<T> {
        safeContext.internalGetEntities(kClass, fastVector, range, receiver, predicate)
        return receiver
    }

    /**
     * Retrieves a list of entities of type [T] within the specified range.
     * This method is optimized for performance and should be used when possible.
     */
    @EntityDslMarker
    fun buildFast(): List<T> {
        safeContext.internalGetFastEntities(kClass, fastVector, range, receiver, predicate)
        return receiver
    }
}

/**
 * Initiates an entity search operation in the world at the specified position using an [EntityDsl].
 *
 * @param pos The position to start the search from. Defaults to the player's current position.
 * @param range The range around the position to search for entities.
 * @param predicate The predicate to filter entities.
 * @return A list of entities matching the predicate within the specified range.
 */
@EntityDslMarker
inline fun <reified T : Entity> SafeContext.entitySearch(
    range: Double,
    pos: BlockPos = player.blockPos,
    noinline predicate: (T) -> Boolean = { true },
): List<T> = EntityDsl(this, T::class, pos, range, predicate).build()

/**
 * Initiates an optimized entity search operation in the world at the specified position using an [EntityDsl].
 *
 * @param pos The position to start the search from. Defaults to the player's current position.
 * @param range The range around the position to search for entities.
 * @param predicate The predicate to filter entities.
 * @return A list of entities matching the predicate within the specified range.
 */
@EntityDslMarker
inline fun <reified T : Entity> SafeContext.fastEntitySearch(
    range: Double,
    pos: BlockPos = player.blockPos,
    noinline predicate: (T) -> Boolean = { true },
): List<T> = EntityDsl(this, T::class, pos, range, predicate).buildFast()
