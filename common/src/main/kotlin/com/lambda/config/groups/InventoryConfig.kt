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

package com.lambda.config.groups

import com.lambda.interaction.material.ContainerSelection
import com.lambda.interaction.material.ContainerSelection.Companion.selectContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.util.item.ItemUtils
import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.item.ToolItem
import net.minecraft.item.ToolMaterial
import net.minecraft.item.ToolMaterials

interface InventoryConfig {
    val disposables: Set<Block>
    val swapWithDisposables: Boolean
    val providerPriority: Priority
    val storePriority: Priority

    val accessShulkerBoxes: Boolean
    val accessEnderChest: Boolean
    val accessChests: Boolean
    val accessStashes: Boolean

    val containerSelection: ContainerSelection get() = selectContainer {
        val allowedContainers = mutableSetOf<MaterialContainer.Rank>().apply {
            addAll(MaterialContainer.Rank.entries)
            if (!accessShulkerBoxes) remove(MaterialContainer.Rank.SHULKER_BOX)
            if (!accessEnderChest) remove(MaterialContainer.Rank.ENDER_CHEST)
            if (!accessChests) remove(MaterialContainer.Rank.CHEST)
            if (!accessStashes) remove(MaterialContainer.Rank.STASH)
        }
        ofAnyType(*allowedContainers.toTypedArray())
    }

    val useWoodenTools: Boolean
    val useStoneTools: Boolean
    val useIronTools: Boolean
    val useDiamondTools: Boolean
    val useNetheriteTools: Boolean
    val useGoldTools: Boolean
    val useShears: Boolean
    val useFlintAndSteel: Boolean

    val allowedTools get() = mutableSetOf<Item>().apply {
        addAll(ItemUtils.tools)
        if (!useWoodenTools) removeIf { it is ToolItem && it.material == ToolMaterials.WOOD }
        if (!useStoneTools) removeIf { it is ToolItem && it.material == ToolMaterials.STONE }
        if (!useIronTools) removeIf { it is ToolItem && it.material == ToolMaterials.IRON }
        if (!useDiamondTools) removeIf { it is ToolItem && it.material == ToolMaterials.DIAMOND }
        if (!useNetheriteTools) removeIf { it is ToolItem && it.material == ToolMaterials.NETHERITE }
        if (!useGoldTools) removeIf { it is ToolItem && it.material == ToolMaterials.GOLD }
        if (!useShears) removeIf { it == Items.SHEARS }
        if (!useFlintAndSteel) removeIf { it == Items.FLINT_AND_STEEL }
    }

    enum class Priority {
        WithMinItems,
        WithMaxItems;

        fun materialComparator(selection: StackSelection) =
            when (this) {
                WithMaxItems -> compareBy<MaterialContainer> { it.rank }
                    .thenByDescending { it.materialAvailable(selection) }
                    .thenBy { it.name }

                WithMinItems -> compareBy<MaterialContainer> { it.rank }
                    .thenBy { it.materialAvailable(selection) }
                    .thenBy { it.name }
            }

        fun spaceComparator(selection: StackSelection) =
            when (this) {
                WithMaxItems -> compareBy<MaterialContainer> { it.rank }
                    .thenByDescending { it.spaceAvailable(selection) }
                    .thenBy { it.name }

                WithMinItems -> compareBy<MaterialContainer> { it.rank }
                    .thenBy { it.spaceAvailable(selection) }
                    .thenBy { it.name }
            }
    }
}