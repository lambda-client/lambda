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

package com.lambda.interaction.inventory.container.containers.external

import com.lambda.Lambda.mc
import com.lambda.context.AutomatedSafeContext
import com.lambda.interaction.handlers.ContainerHandler
import com.lambda.interaction.inventory.container.Container
import com.lambda.interaction.inventory.container.ExternalContainer
import com.lambda.task.TaskGenerator
import com.lambda.task.TaskOrNullGenerator
import com.lambda.task.tasks.AcquirePlacedBlockTask
import com.lambda.task.tasks.BuildTask.Companion.breakAndCollectBlock
import com.lambda.task.tasks.OpenContainerTask
import com.lambda.util.extension.containerSlots
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import net.minecraft.block.Blocks
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.item.ItemStack

object EnderChestContainer : Container(Rank.EnderChest), ExternalContainer {
	override val slots
		get() =
			if (ContainerHandler.lastInteractedBlockEntity is EnderChestBlockEntity) {
				mc.player?.currentScreenHandler?.containerSlots ?: emptyList()
			} else emptyList()
	override var stacks = emptyList<ItemStack>()

	override val description = buildText { literal("Ender Chest") }

	context(automatedSafeContext: AutomatedSafeContext)
	override fun accessThen(
		closeAfter: Boolean,
		afterClose: TaskOrNullGenerator<Unit>?,
		afterOpen: TaskGenerator<Unit>
	) =
		AcquirePlacedBlockTask(
			Blocks.ENDER_CHEST,
			automated = automatedSafeContext
		).then { pos ->
			OpenContainerTask(pos, automatedSafeContext).then {
				afterOpen.invoke(automatedSafeContext, Unit).thenOrNull {
					if (closeAfter) {
						player.closeHandledScreen()
						automatedSafeContext.breakAndCollectBlock(pos, lifeMaintenance = false)
					} else null
				}
			}
		}
}
