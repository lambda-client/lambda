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

package com.lambda.interaction.material.container.containers

import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.interaction.handlers.ContainerHandler
import com.lambda.interaction.handlers.ContainerHandler.findSlotsWithMaterial
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.ExternalContainer
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.task.wrappers.TaskSupplier
import com.lambda.task.tasks.BuildTask.Companion.breakAndCollectBlock
import com.lambda.task.tasks.OpenContainerTask
import com.lambda.task.tasks.PlaceContainerTask
import com.lambda.task.wrappers.thenAction
import com.lambda.task.wrappers.thenOrNull
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
			if (ContainerHandler.lastInteractedBlockEntity is EnderChestBlockEntity)
				safeContext.player.currentScreenHandler.containerSlots
			else emptyList()
	override var stacks = emptyList<ItemStack>()

	override val description = buildText { literal("Ender Chest") }

	context(automatedSafeContext: AutomatedSafeContext)
	override fun accessThen(exitAfter: Boolean, taskSupplier: TaskSupplier<Unit, Unit>) =
		Items.ENDER_CHEST
			.select()
			.findSlotsWithMaterial()
			.firstOrNull()?.let { slot ->
				PlaceContainerTask(slot, automatedSafeContext).thenAction { pos ->
					OpenContainerTask(pos, automatedSafeContext).thenAction {
						taskSupplier.invoke(automatedSafeContext, Unit).thenOrNull {
							if (exitAfter) automatedSafeContext.breakAndCollectBlock(pos, lifeMaintenance = false)
							else null
						}
					}
				}
			}
}
