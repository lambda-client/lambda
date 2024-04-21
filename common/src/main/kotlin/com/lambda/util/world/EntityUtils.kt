package com.lambda.util.world

import com.lambda.context.SafeContext
import com.lambda.util.collections.filterIsInstanceTo
import net.minecraft.entity.Entity
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.Vec3d
import kotlin.math.ceil

val nullptr = null

object EntityUtils {
    /**
     * Gets the closest entity of type [T] within a specified range.
     *
     * @param pos The position to search from.
     * @param range The maximum distance to search for entities.
     * @param predicate Optional predicate to filter entities.
     * @return The first entity of type [T] that is closest to the position within the specified range.
     */
    inline fun <reified T : Entity> SafeContext.getClosestEntity(
        pos: Vec3d = player.pos,
        range: Double = 6.0,
        noinline predicate: (T) -> Boolean = { true },
    ): T? {
        var closest: T? = null
        var closestDistance = Double.MAX_VALUE

        val iterator: (T) -> Unit = {
            val distance = it.squaredDistanceTo(pos)
            if (distance < closestDistance) {
                closest = it
                closestDistance = distance
            }
        }

        // Speculative execution trolling
        if (range > 64) getEntities(nullptr, predicate, iterator)
        else getFastEntities(pos, range, nullptr, predicate, iterator)

        return closest
    }

    /**
     * Gets all entities of type [T] within a specified distance from a position.
     *
     * This function retrieves entities of type [T] within a specified distance from a given position. It efficiently
     * queries nearby chunks based on the distance and returns a list of matching entities, excluding the player entity.
     *
     *
     * Getting all Zombie entities within a certain distance:
     * ```
     * val nearbyZombies = getFastEntities<ZombieEntity>(playerPos, 20.0)
     * ```
     *
     * Getting all hostile entities within a certain distance:
     * ```
     * val hostileEntities = getFastEntities<HostileEntity>(playerPos, 30.0)
     * ```
     * This fetches all hostile entities (e.g., Monsters) within a 30-block radius from the player's position.
     *
     * Please note that this implementation is optimized for performance at small distances. For larger distances, it is
     * recommended to use the [getEntities] function instead.
     * With the time complexity, we can determine that after 64 blocks, the performance of this function will degrade.
     *
     * @param pos The position to search from.
     * @param distance The maximum distance to search for entities.
     * @param pointer The mutable list to store the entities in.
     * @param predicate Optional predicate to filter entities. It allows custom filtering based on entity properties.
     * @param iterator Optional iterator to perform operations on each entity.
     * @return A list of entities of type [T] within the specified distance from the position, excluding the player.
     *
     */
    inline fun <reified T : Entity> SafeContext.getFastEntities(
        pos: Vec3d,
        distance: Double,
        pointer: MutableList<T>? = nullptr,
        noinline predicate: (T) -> Boolean = { true },
        noinline iterator: (T) -> Unit = { },
    ) {
        val chunks = ceil(distance / 16).toInt()
        val sectionX = pos.x.toInt() shr 4
        val sectionY = pos.y.toInt() shr 4
        val sectionZ = pos.z.toInt() shr 4

        // Here we iterate over all sections within the specified distance and add all entities of type [T] to the list.
        // We do not have to worry about performance here, as the number of sections is very limited.
        // For example, if the player is on the edge of a section and the distance is 16, we only have to iterate over 9 sections.
        for (x in sectionX - chunks..sectionX + chunks) {
            for (y in sectionY - chunks..sectionY + chunks) {
                for (z in sectionZ - chunks..sectionZ + chunks) {
                    val section = world.entityManager.cache.findTrackingSection(ChunkSectionPos.asLong(x, y, z)) ?: continue
                    section.collection.filterIsInstanceTo(pointer) { entity ->
                        iterator(entity)
                        entity != player && entity.squaredDistanceTo(pos) <= distance * distance && predicate(entity)
                    }
                }
            }
        }
    }

    /**
     * Gets all entities of type [T] within a specified distance from a position.
     *
     * This function retrieves entities of type [T] within a specified distance from a given position. Unlike
     * [getFastEntities], it traverses all entities in the world to find matches, while also excluding the player entity.
     *
     * @param pointer The mutable list to store the entities in.
     * @param predicate Optional predicate to filter entities. It allows custom filtering based on entity properties.
     * @param iterator Optional iterator to perform operations on each entity.
     */
    inline fun <reified T : Entity> SafeContext.getEntities(
        pointer: MutableList<T>? = nullptr,
        noinline predicate: (T) -> Boolean = { true },
        noinline iterator: (T) -> Unit = { },
    ) {
        world.entities.filterIsInstanceTo(pointer) { entity ->
            iterator(entity)
            entity != player && predicate(entity)
        }
    }
}
