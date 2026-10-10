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

package com.lambda.interaction.container.containers.external

import com.lambda.Lambda.mc
import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.interaction.container.BasicOpenedContainerContext
import com.lambda.interaction.container.ContainerType
import com.lambda.interaction.container.OpenContainerTask
import com.lambda.interaction.container.OpenedContainerContext
import com.lambda.interaction.container.PlacedContainer
import com.lambda.interaction.handler.handlers.ContainerHandler.lastInteractedBlockEntity
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.task.tasks.openContainer
import com.lambda.util.extension.containerSlots
import com.lambda.util.player.SlotUtils.typeSafe
import com.lambda.util.text.buildText
import com.lambda.util.text.highlighted
import com.lambda.util.text.literal
import net.minecraft.block.ChestBlock
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.Slot
import net.minecraft.util.math.BlockPos

data class ChestContainer(
	override val pos: BlockPos,
	override var stacks: List<ItemStack>,
	override val stash: StashContainer? = null
) : PlacedContainer(ContainerType.Chest) {
    override val slots
        get(): List<Slot> =
            if (isAccessed) mc.player?.currentScreenHandler?.containerSlots ?: emptyList()
            else emptyList()

    override val description =
	    buildText {
		    literal("Chest at ")
		    highlighted(pos.toShortString())
		    stash?.let { stash ->
			    literal(" (contained in ")
			    highlighted(stash.name)
			    literal(")")
		    }
	    }

	override val isAccessed
		get() = lastInteractedBlockEntity?.let { blockEntity ->
			blockEntity.pos == pos &&
					blockEntity.cachedState.block is ChestBlock &&
					mc.player?.currentScreenHandler?.typeSafe == ScreenHandlerType.GENERIC_9X3
		} ?: false

	@Ta5kBuilder
	context(automated: Automated)
	override fun access() =
		if (isAccessed) null
		else AccessChestTask(automated)

	inner class AccessChestTask @Ta5kBuilder internal constructor(
		automated: Automated
	) : OpenContainerTask<OpenedContainerContext>(description), Automated by automated {
		override fun SafeContext.onStart() {
			openContainer(pos)
				.onSuccess { success(BasicOpenedContainerContext(::isAccessed)) }
				.start()
		}
	}
}