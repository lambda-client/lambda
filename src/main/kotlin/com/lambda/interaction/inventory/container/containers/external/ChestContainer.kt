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
import com.lambda.interaction.inventory.container.ExternalContainer
import com.lambda.interaction.inventory.container.PlacedContainer
import com.lambda.task.tasks.OpenContainerTask.Companion.openContainer
import com.lambda.task.tasks.SimpleActionTask.Companion.simpleAction
import com.lambda.task.wrappers.TaskOrNullSupplier
import com.lambda.task.wrappers.thenOrNull
import com.lambda.util.extension.containerSlots
import com.lambda.util.text.buildText
import com.lambda.util.text.highlighted
import com.lambda.util.text.literal
import net.minecraft.block.ChestBlock
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.Slot
import net.minecraft.util.math.BlockPos

data class ChestContainer(
	val blockPos: BlockPos,
	override var stacks: List<ItemStack>,
	override val stash: StashContainer? = null
) : Container(Rank.Chest), ExternalContainer, PlacedContainer {
    override val slots
        get(): List<Slot> =
            lastInteractedBlockEntity?.let { blockEntity ->
                if (blockEntity is ChestBlockEntity && blockEntity.pos == blockPos) {
                    mc.player?.currentScreenHandler?.containerSlots
                } else emptyList()
            } ?: emptyList()

    override val description =
	    buildText {
		    literal("Chest at ")
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
					blockEntity.cachedState.block is ChestBlock &&
					mc.player?.currentScreenHandler?.type == ScreenHandlerType.GENERIC_9X3
		} ?: false

	context(automatedSafeContext: AutomatedSafeContext)
	override fun <R> accessThen(
		closeAfter: Boolean,
		afterOpen: TaskOrNullSupplier<Unit, R?>,
		afterClose: TaskOrNullSupplier<R?, *>
	) =
		with(automatedSafeContext) {
			taskOrSkipOrNull(
				optional = {
					openContainer(blockPos).thenOrNull {
						afterOpen(Unit)
					}
				}
			) {
				if (closeAfter) {
					simpleAction("Close inventory") { player.closeHandledScreen() }
				} else null
			}
		}
}