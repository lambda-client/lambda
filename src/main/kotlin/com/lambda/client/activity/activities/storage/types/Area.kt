package com.lambda.client.activity.activities.storage.types

import com.lambda.client.util.math.CoordinateConverter.asString
import net.minecraft.client.entity.EntityPlayerSP
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

    val minWidth: Int
        get() = minOf(maxX - minX + 1, maxZ - minZ + 1)

    fun closestBlockPos(player: EntityPlayerSP) = containedBlocks.minBy {
        it.distanceSq(player.posX, player.posY, player.posZ)
    }

    fun grow(amount: Int) = Area(
        BlockPos(pos1.x - amount, pos1.y - amount, pos1.z - amount),
        BlockPos(pos2.x + amount, pos2.y + amount, pos2.z + amount)
    )

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