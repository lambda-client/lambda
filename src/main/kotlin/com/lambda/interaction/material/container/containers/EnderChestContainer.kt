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

package com.lambda.interaction.material.container.containers

import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.ContainerManager
import com.lambda.interaction.material.container.ExternalContainer
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.task.tasks.AcquireMaterialTask.Companion.acquire
import com.lambda.task.tasks.OpenContainerTask
import com.lambda.task.tasks.PlaceContainerTask
import com.lambda.util.extension.containerSlots
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items

object EnderChestContainer : MaterialContainer(Rank.EnderChest), ExternalContainer {
    context(safeContext: SafeContext)
    override val slots
        get() =
            if (ContainerManager.lastInteractedBlockEntity is EnderChestBlockEntity)
                safeContext.player.currentScreenHandler.containerSlots
            else emptyList()
    override var stacks = emptyList<ItemStack>()

    override val description = buildText { literal("Ender Chest") }

    context(automated: Automated)
    override fun access() =
        automated.acquire { Items.ENDER_CHEST.select() }.thenOrNull { selection ->
            val slot = selection.filterSlots(HotbarContainer.slots).firstOrNull() ?: return@thenOrNull FailureTask("Ender Chest not found in hotbar after transfer.")
            PlaceContainerTask(slot, automated).finally { pos ->
                OpenContainerTask(pos, automated)
            }
        }
}
