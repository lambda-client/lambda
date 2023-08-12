package com.lambda.client.activity.activities.storage.types

import com.lambda.client.util.math.CoordinateConverter.asString
import net.minecraft.item.Item
import net.minecraft.util.math.BlockPos

/**
 * [Stash] is a data class that represents a stash of [Item]s.
 * @param area The area that the stash is located in.
 * @param items The items that are in the stash.
 */
data class Stash(val area: Area, val items: List<Item>) {
    override fun toString() = "Stash(${
        items.joinToString {
            it.registryName.toString().split(":").last()
        }
    })@(${area.center.asString()})"

    companion object {
        val EMPTY = Stash(Area(BlockPos.ORIGIN, BlockPos.ORIGIN), emptyList())
    }
}