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
import com.lambda.interaction.handler.handlers.ContainerHandler.lastInteractedBlockEntity
import com.lambda.interaction.inventory.container.Container
import com.lambda.interaction.inventory.container.PlacedContainer
import com.lambda.task.TaskGenerator
import com.lambda.task.TaskOrNullGenerator
import com.lambda.task.tasks.NoopTask.Companion.noopTask
import com.lambda.task.tasks.OpenContainerTask
import com.lambda.task.tasks.SimpleActionTask.Companion.simpleAction
import com.lambda.util.extension.containerSlots
import com.lambda.util.text.buildText
import com.lambda.util.text.highlighted
import com.lambda.util.text.literal
import net.minecraft.block.Block
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.Slot
import net.minecraft.util.math.BlockPos

class PlacedShulkerBoxContainer(
	val blockPos: BlockPos,
	val block: Block,
	override val stash: StashContainer?,
	override var stacks: List<ItemStack>
) : Container(Rank.PlacedShulkerBox), PlacedContainer {
	override val slots: List<Slot>
		get() =
			if (isAccessed) mc.player?.currentScreenHandler?.containerSlots ?: emptyList()
			else emptyList()

	override val description =
		buildText {
			literal("Placed shulker box at ")
			highlighted(blockPos.toShortString())
			stash?.let { stash ->
				literal(" (contained in ")
				highlighted(stash.name)
				literal(")")
			}
		}

	override val isAccessed
		get() = lastInteractedBlockEntity?.let { blockEntity ->
			blockEntity.pos == blockPos &&
					blockEntity.cachedState.block == block &&
					mc.player?.currentScreenHandler?.type == ScreenHandlerType.SHULKER_BOX
		} ?: false

	context(automatedSafeContext: AutomatedSafeContext)
	override fun accessThen(
		closeAfter: Boolean,
		afterClose: TaskOrNullGenerator<Unit>?,
		afterOpen: TaskGenerator<Unit>
	) =
		run {
			if (!isAccessed) OpenContainerTask(blockPos, automated = automatedSafeContext)
			else noopTask()
		}.then {
			afterOpen(Unit).thenOrNull {
				if (closeAfter) {
					simpleAction("Close Inventory") { automatedSafeContext.player.closeHandledScreen() }
				} else null
			}
		}
}