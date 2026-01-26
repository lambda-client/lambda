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

import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.ContainerManager
import com.lambda.interaction.material.container.ContainerManager.findSlotsWithMaterial
import com.lambda.interaction.material.container.ExternalContainer
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.task.TaskGenerator
import com.lambda.task.tasks.BuildTask.Companion.breakAndCollectBlock
import com.lambda.task.tasks.OpenContainerTask
import com.lambda.task.tasks.PlaceContainerTask
import com.lambda.util.extension.containerSlots
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos

object EnderChestContainer : MaterialContainer(Rank.EnderChest), ExternalContainer {
	context(safeContext: SafeContext)
	override val slots
		get() =
			if (ContainerManager.lastInteractedBlockEntity is EnderChestBlockEntity)
				safeContext.player.currentScreenHandler.containerSlots
			else emptyList()
	override var stacks = emptyList<ItemStack>()

	override val description = buildText { literal("Ender Chest") }

	private var placePos = BlockPos.ORIGIN

	context(automatedSafeContext: AutomatedSafeContext)
	override fun accessThen(exitAfter: Boolean, taskGenerator: TaskGenerator<Unit>) =
		Items.ENDER_CHEST
			.select()
			.findSlotsWithMaterial()
			.firstOrNull()?.let { slot ->
				PlaceContainerTask(slot, automatedSafeContext).then { pos ->
					placePos = pos
					OpenContainerTask(pos, automatedSafeContext).then {
						taskGenerator.invoke(automatedSafeContext, Unit).thenOrNull {
							if (exitAfter) automatedSafeContext.breakAndCollectBlock(placePos, lifeMaintenance = false)
							else null
						}
					}
				}
			}
}
