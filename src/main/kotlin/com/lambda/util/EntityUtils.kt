/*
 * Copyright 2026 Lambda
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

package com.lambda.util

import com.lambda.util.DynamicReflectionSerializer.remappedName
import com.lambda.util.ReflectionUtils.scanResult
import com.lambda.util.math.MathUtils.floorToInt
import io.github.classgraph.ClassInfo
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d

@Suppress("unused")
object EntityUtils {
    val entities: Collection<ClassInfo> = scanResult
        .getSubclasses(Entity::class.java)
        .filter { !it.isAbstract && it.name.startsWith("net.minecraft") }

    val playerEntityMap = createEntityNameMap("net.minecraft.client.network.")
    val mobEntityMap = createEntityNameMap("net.minecraft.entity.mob.")
    val passiveEntityMap = createEntityNameMap("net.minecraft.entity.passive.")
    val vehicleEntityMap = createEntityNameMap("net.minecraft.entity.vehicle.")
    val projectileEntityMap = createEntityNameMap("net.minecraft.entity.projectile.")
    val bossEntityMap = createEntityNameMap("net.minecraft.entity.boss.")
    val decorationEntityMap = createEntityNameMap("net.minecraft.entity.decoration.")
    val blockEntityMap = createBlockEntityNameMap()
    val miscEntityMap = createEntityNameMap("net.minecraft.entity.", strictDir = true)

    enum class EntityGroup(val nameToDisplayNameMap: Map<String, String>) {
        Player(playerEntityMap),
        Mob(mobEntityMap),
        Passive(passiveEntityMap),
        Vehicle(vehicleEntityMap),
        Projectile(projectileEntityMap),
        Boss(bossEntityMap),
        Decoration(decorationEntityMap),
        Block(blockEntityMap),
        Misc(miscEntityMap)
    }

    val Entity.entityGroup get() = entityGroup()
    val BlockEntity.entityGroup: EntityGroup get() = entityGroup()

    private fun Any.entityGroup(): EntityGroup {
        val simpleName = javaClass.simpleName
        return EntityGroup.entries.firstOrNull { simpleName in it.nameToDisplayNameMap } ?: EntityGroup.Misc
    }

    fun Box.getClosestPointTo(vec: Vec3d) =
        Vec3d(
            vec.x.coerceIn(minX, maxX),
            vec.y.coerceIn(minY, maxY),
            vec.z.coerceIn(minZ, maxZ)
        )

    fun Entity.getPositionsWithinHitbox() =
        getPositionsWithinHitboxXZ(boundingBox.minY.floorToInt(), boundingBox.maxY.floorToInt())

    fun Entity.getPositionsWithinHitboxXZ(minY: Int, maxY: Int) =
        boundingBox.getPositionsWithinBoxXZ(minY, maxY)

    fun Box.getPositionsWithinBox() =
        getPositionsWithinBoxXZ(minY.floorToInt(), maxY.floorToInt())

    fun Box.getPositionsWithinBoxXZ(minY: Int, maxY: Int): HashSet<BlockPos> {
        val minX = minX.floorToInt()
        val maxX = maxX.floorToInt()
        val minZ = minZ.floorToInt()
        val maxZ = maxZ.floorToInt()
        val positions = hashSetOf<BlockPos>()
        (minX..maxX).forEach { x ->
            (minY..maxY).forEach { y ->
                (minZ..maxZ).forEach { z ->
                    positions.add(BlockPos(x, y, z))
                }
            }
        }
        return positions
    }

    private fun createEntityNameMap(directory: String, strictDir: Boolean = false) =
        entities.createNameMap(directory, "Entity", strictDir)

    private fun createBlockEntityNameMap() =
        scanResult
            .getSubclasses(BlockEntity::class.java)
            .filter { !it.isAbstract }
            .createNameMap("net.minecraft.block.entity", "BlockEntity")

    fun Collection<ClassInfo>.createNameMap(
        directory: String,
        removePattern: String = "",
        strictDirectory: Boolean = false
    ) = map {
        val remappedName = it.name.remappedName
        val displayName = remappedName
            .substring(remappedName.indexOfLast { it == '.' } + 1)
            .replace(removePattern, "")
            .fancyFormat()
        MappingInfo(it.simpleName, remappedName, displayName)
    }.sortedBy { it.displayName.lowercase() }
        .filter { info ->
            if (strictDirectory)
                info.remapped.startsWith(directory) && !info.remapped.substring(directory.length).contains(".")
            else info.remapped.startsWith(directory)
        }
        .associate { it.raw to it.displayName }

    private fun String.fancyFormat() =
        replace("$", " - ").replace("(?<!\\s)[A-Z]".toRegex(), " $0")

    private data class MappingInfo(
        val raw: String,
        val remapped: String,
        val displayName: String
    )
}