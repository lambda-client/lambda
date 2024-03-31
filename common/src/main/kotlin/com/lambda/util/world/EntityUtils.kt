package com.lambda.util.world

import com.lambda.context.SafeContext
import com.lambda.util.collections.filterIsInstanceTo
import net.minecraft.entity.Entity
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.Vec3d
import kotlin.math.ceil


/**
 * Utility class for working with entities in a Minecraft environment.
 */
object EntityUtils {

    // TODO: Tick cache implementation

    /**
     * Gets the closest entity of type [T] within a specified range.
     *
     * @param pos The position to search from.
     * @param range The maximum distance to search for entities.
     * @param predicate Optional predicate to filter entities.
     * @return The closest entity of type [T] within the specified range, or null if none is found.
     */
    inline fun <reified T : Entity> SafeContext.getClosestEntity(
        pos: Vec3d,
        range: Double,
        noinline predicate: (T) -> Boolean = { true },
    ): T? {
        return getFastEntities(pos, range, predicate).firstOrNull { it.pos.squaredDistanceTo(pos) <= range * range }
    }

    /**
     * Gets all entities of type [T] within a specified distance from a position.
     *
     * @param pos The position to search from.
     * @param distance The maximum distance to search for entities.
     * @param predicate Optional predicate to filter entities.
     * @return A list of entities of type [T] within the specified distance from the position.
     */
    inline fun <reified T : Entity> SafeContext.getFastEntities(
        pos: Vec3d,
        distance: Double,
        noinline predicate: (T) -> Boolean = { true },
    ): List<T> {
        val chunks = ceil(distance / 16).toInt()
        val sectionX = pos.x.toInt() shr 4
        val sectionY = pos.y.toInt() shr 4
        val sectionZ = pos.z.toInt() shr 4

        val entities = ArrayList<T>()

        // Here we iterate over all sections within the specified distance and add all entities of type [T] to the list.
        // We do not have to worry about performance here, as the number of sections is very limited.
        // For example, if the player is on the edge of a section and the distance is 16, we only have to iterate over 9 sections.
        for (x in sectionX - chunks..sectionX + chunks) {
            for (y in sectionY - chunks..sectionY + chunks) {
                for (z in sectionZ - chunks..sectionZ + chunks) {
                    val section = world.entityManager.cache.findTrackingSection(ChunkSectionPos.asLong(x, y, z)) ?: continue
                    section.collection.filterIsInstanceTo(entities, predicate)
                }
            }
        }

        return entities
    }
}
