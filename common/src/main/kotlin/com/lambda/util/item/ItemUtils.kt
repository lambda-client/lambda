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

package com.lambda.util.item

import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.item.Item
import net.minecraft.item.Items

object ItemUtils {
    val pickaxes = setOf(
        Items.WOODEN_PICKAXE,
        Items.STONE_PICKAXE,
        Items.IRON_PICKAXE,
        Items.GOLDEN_PICKAXE,
        Items.DIAMOND_PICKAXE,
        Items.NETHERITE_PICKAXE,
    )

    val shovels = setOf(
        Items.WOODEN_SHOVEL,
        Items.STONE_SHOVEL,
        Items.IRON_SHOVEL,
        Items.GOLDEN_SHOVEL,
        Items.DIAMOND_SHOVEL,
        Items.NETHERITE_SHOVEL,
    )

    val axes = setOf(
        Items.WOODEN_AXE,
        Items.STONE_AXE,
        Items.IRON_AXE,
        Items.GOLDEN_AXE,
        Items.DIAMOND_AXE,
        Items.NETHERITE_AXE,
    )

    val hoes = setOf(
        Items.WOODEN_HOE,
        Items.STONE_HOE,
        Items.IRON_HOE,
        Items.GOLDEN_HOE,
        Items.DIAMOND_HOE,
        Items.NETHERITE_HOE,
    )

    val swords = setOf(
        Items.WOODEN_SWORD,
        Items.STONE_SWORD,
        Items.IRON_SWORD,
        Items.GOLDEN_SWORD,
        Items.DIAMOND_SWORD,
        Items.NETHERITE_SWORD,
    )

    val misc = setOf(
        Items.SHEARS,
    )

    val tools = pickaxes + shovels + axes + hoes + swords + misc

    val shulkerBoxes = setOf(
        Items.SHULKER_BOX,
        Items.WHITE_SHULKER_BOX,
        Items.ORANGE_SHULKER_BOX,
        Items.MAGENTA_SHULKER_BOX,
        Items.LIGHT_BLUE_SHULKER_BOX,
        Items.YELLOW_SHULKER_BOX,
        Items.LIME_SHULKER_BOX,
        Items.PINK_SHULKER_BOX,
        Items.GRAY_SHULKER_BOX,
        Items.LIGHT_GRAY_SHULKER_BOX,
        Items.CYAN_SHULKER_BOX,
        Items.PURPLE_SHULKER_BOX,
        Items.BLUE_SHULKER_BOX,
        Items.BROWN_SHULKER_BOX,
        Items.GREEN_SHULKER_BOX,
        Items.RED_SHULKER_BOX,
        Items.BLACK_SHULKER_BOX,
    )

    val chests = setOf(
        Items.CHEST,
        Items.TRAPPED_CHEST,
        Items.ENDER_CHEST,
        Items.BARREL,
    )

    val defaultDisposables = setOf(
        Blocks.DIRT,
        Blocks.COBBLESTONE,
        Blocks.GRANITE,
        Blocks.DIORITE,
        Blocks.ANDESITE,
        Blocks.SANDSTONE,
        Blocks.RED_SANDSTONE,
        Blocks.NETHERRACK,
        Blocks.END_STONE,
        Blocks.STONE,
        Blocks.BASALT,
        Blocks.BLACKSTONE,
        Blocks.COBBLED_DEEPSLATE
    )

    val Item.block: Block get() = Block.getBlockFromItem(this)

    fun Int.toItemCount(): String {
        if (this < 0) {
            return "Invalid input"
        }

        return buildString {
            val dubs = this@toItemCount / (54 * 27)
            val shulkers = (this@toItemCount % (54 * 27)) / 64
            val remainingItems = this@toItemCount % 64

            if (dubs > 0) {
                append("$dubs dub")
                if (dubs > 1) {
                    append("s")
                }
                if (shulkers > 0 || remainingItems > 0) {
                    append(" ")
                }
            }

            if (shulkers > 0) {
                append("$shulkers shulker")
                if (shulkers > 1) {
                    append("s")
                }
                if (remainingItems > 0) {
                    append(" ")
                }
            }

            if (remainingItems > 0) {
                append("$remainingItems item")
                if (remainingItems > 1) {
                    append("s")
                }
            }
        }
    }
}
