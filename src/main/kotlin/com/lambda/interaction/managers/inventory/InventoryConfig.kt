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

package com.lambda.interaction.managers.inventory

import com.lambda.config.ISettingGroup
import com.lambda.event.events.TickEvent
import com.lambda.interaction.material.ContainerSelection
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.util.Describable
import com.lambda.util.NamedEnum
import net.minecraft.block.Block

interface InventoryConfig : ISettingGroup {
    val actionsPerSecond: Int
    val tickStageMask: Collection<TickEvent>
    val disposables: Collection<Block>
    val swapWithDisposables: Boolean
    val providerPriority: Priority
    val storePriority: Priority

    val immediateAccessOnly: Boolean
    val accessShulkerBoxes: Boolean
    val accessEnderChest: Boolean
    val accessChests: Boolean
    val accessStashes: Boolean

    val containerSelection: ContainerSelection
        get() = ContainerSelection.selectContainer {
            val allowedContainers = buildSet {
                addAll(MaterialContainer.Rank.entries)
                if (!accessShulkerBoxes || immediateAccessOnly) remove(MaterialContainer.Rank.ShulkerBox)
                if (!accessEnderChest || immediateAccessOnly) remove(MaterialContainer.Rank.EnderChest)
                if (!accessChests || immediateAccessOnly) remove(MaterialContainer.Rank.Chest)
                if (!accessStashes || immediateAccessOnly) remove(MaterialContainer.Rank.Stash)
            }
            ofAnyType(*allowedContainers.toTypedArray())
        }

    enum class Priority(
        override val displayName: String,
        override val description: String
    ) : NamedEnum, Describable {
        WithMinItems("With Min Items", "Pick containers with the fewest matching items (or least space) first; useful for topping off or clearing leftovers."),
        WithMaxItems("With Max Items", "Pick containers with the most matching items (or most space) first; ideal for bulk moves with fewer transfers.");

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
