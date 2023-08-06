package com.lambda.client.activity.activities.storage

import com.lambda.client.util.math.CoordinateConverter.asString
import net.minecraft.util.math.BlockPos

/**
 * [Area] is a data class that represents an area in the world.
 * @param pos1 The first position of the area.
 * @param pos2 The second position of the area.
 */
data class Area(val pos1: BlockPos, val pos2: BlockPos) {
    val center: BlockPos
        get() = BlockPos(
            (pos1.x + pos2.x) / 2,
            (pos1.y + pos2.y) / 2,
            (pos1.z + pos2.z) / 2
        )

    val containedBlocks: Set<BlockPos>
        get() = BlockPos.getAllInBox(pos1, pos2).toSet()

    val maxWidth: Int
        get() = maxOf(maxX - minX + 1, maxZ - minZ + 1)

    val minX: Int
        get() = minOf(pos1.x, pos2.x)
    val minY: Int
        get() = minOf(pos1.y, pos2.y)
    val minZ: Int
        get() = minOf(pos1.z, pos2.z)
    val maxX: Int
        get() = maxOf(pos1.x, pos2.x)
    val maxY: Int
        get() = maxOf(pos1.y, pos2.y)
    val maxZ: Int
        get() = maxOf(pos1.z, pos2.z)

    override fun toString() = "Area(${center.asString()})"
}