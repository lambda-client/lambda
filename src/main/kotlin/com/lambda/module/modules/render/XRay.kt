/*
 * Copyright 2025 Lambda
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

package com.lambda.module.modules.render

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks

object XRay : Module(
    name = "XRay",
    description = "Allows you to see ores through walls",
    tag = ModuleTag.RENDER,
) {
    private val defaultBlocks = setOf(
        Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
        Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
        Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
        Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
        Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
        Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
        Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
        Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
        Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE,
        Blocks.ANCIENT_DEBRIS
    )

    private val selection by setting("Block Selection", defaultBlocks, defaultBlocks, "Block selection that will be shown (whitelist) or hidden (blacklist)")
    private val mode by setting("Selection Mode", Selection.Whitelist, "The mode of the block selection")

    @JvmStatic
    fun isSelected(blockState: BlockState) = mode.select(blockState)

    enum class Selection(val select: (BlockState) -> Boolean) {
        Whitelist({ it.block in selection }),
        Blacklist({ it.block !in selection })
    }

    init {
        onToggle {
            mc.worldRenderer.reload()
        }
    }
}
