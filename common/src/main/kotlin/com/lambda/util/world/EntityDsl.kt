@file:OptIn(InternalApi::class)

package com.lambda.util.world

import com.lambda.context.SafeContext
import com.lambda.core.annotations.InternalApi
import com.lambda.util.world.WorldUtils.internalGetEntities
import com.lambda.util.world.WorldUtils.internalGetFastEntities
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@DslMarker
annotation class EntityDslMarker

/**
 * The `EntityDsl` class provides a DSLfor performing entity search operations
 * within a specified range in a Minecraft world. It allows for filtering, iterating, and comparing entities
 * of a specific type [T] around a given position.
 *
 * @param safeContext The context in which the entity search is performed, providing safe access to world data.
 * @param kClass The class of the entity type [T].
 * @param pos The position from which to start the entity search.
 *
 * ### Usage Example:
 *
 * ```kotlin
 * val entities = entitySearch<LivingEntity>(player.blockPos) {
 *     range(10.0) // Search for entities within a 10 block radius
 *     filter { it.isAlive } // Filter out dead entities
 *     iterator { println("Found entity: ${it.name.string}") } // Print the name of each entity
 * }.build() // Finalize the entity search, you can also use `buildFast` for optimized performances.
 *
 * println("Found ${entities.size} entities.")
 * ```
 *
 * ```kotlin
 * val closestEntityToFOV = entitySearch<LivingEntity> {
 *    range(10.0) // Search for entities within a 10 block radius
 *    filter { it.isAlive } // Filter out dead entities
 *    comparator { player.rotation dist player.eyePos.rotationTo(it.pos) } // Compare entities based on their distance to the player's field of view
 * }.minBy() // Finalize the entity search and return the entity with the smallest value based on the comparator
 *
 * println("Closest entity to FOV: ${closestEntityToFOV}")
 * ```
 */
@EntityDslMarker
class EntityDsl<T : Entity>(
    private val safeContext: SafeContext,
    private val kClass: KClass<out T>,
    pos: BlockPos,
) {
    private val fastVector = pos.toFastVec()
    private val receiver: MutableList<T> = mutableListOf()

    private var range: Double = 8.0
    private var predicate: (T) -> Boolean = { true }
    private var iterator: (T) -> Unit = { _ -> }
    private var comparator: SafeContext.(T) -> Double = { 0.0 }

    /**
     * Sets the range around the position to search for entities.
     */
    fun range(range: Int): EntityDsl<T> {
        this.range = range.toDouble()
        return this
    }

    /**
     * Sets the range around the position to search for entities.
     */
    fun range(range: Double): EntityDsl<T> {
        this.range = range
        return this
    }

    /**
     * Sets a predicate to filter entities.
     */
    fun filter(predicate: (T) -> Boolean): EntityDsl<T> {
        this.predicate = predicate
        return this
    }

    /**
     * Sets an iterator to perform operations on each entity.
     */
    fun iterator(iterator: (T) -> Unit): EntityDsl<T> {
        this.iterator = iterator
        return this
    }

    /**
     * Sets a comparator to compare entities.
     */
    fun comparator(comparator: SafeContext.(T) -> Double): EntityDsl<T> {
        this.comparator = comparator
        return this
    }

    /**
     * Returns the entity that has the smallest value based on the given comparator.
     * If multiple entities have the same value, the first one found will be returned.
     * @return The entity with the smallest value based on the comparator, or null if no entity is found.
     */
    fun minBy(): T? {
        var min: T? = null
        var minValue = Double.MAX_VALUE

        buildFast().forEach { entity ->
            val value = safeContext.comparator(entity)
            if (value < minValue) {
                min = entity
                minValue = value
            }
        }

        return min
    }

    /**
     * Retrieves a list of entities of type [T] within the specified range.
     * This method is safe to use with mutable lists and should be used when the optimized method is not suitable.
     */
    fun build(): List<T> {
        safeContext.internalGetEntities(kClass, fastVector, range, receiver, predicate, iterator)
        return receiver
    }

    /**
     * Retrieves a list of entities of type [T] within the specified range.
     * This method is optimized for performance and should be used when possible.
     */
    fun buildFast(): List<T> {
        safeContext.internalGetFastEntities(kClass, fastVector, range, receiver, predicate, iterator)
        return receiver
    }
}

/**
 * Initiates an entity search operation in the world at the specified position using an [EntityDsl].
 * Don't forget to call [EntityDsl.build] or [EntityDsl.buildFast] to finalize the search.
 *
 * @param pos The position to start the search from. Defaults to the player's current position.
 * @param block The block of code that performs the search using the [EntityDsl].
 */
inline fun <reified T : Entity> SafeContext.entitySearch(pos: BlockPos = player.blockPos, block: (@EntityDslMarker EntityDsl<T>).() -> Unit) = EntityDsl(this, T::class, pos).apply(block)
