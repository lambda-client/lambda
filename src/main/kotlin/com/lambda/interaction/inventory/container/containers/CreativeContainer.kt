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

package com.lambda.interaction.inventory.container.containers

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.interaction.inventory.StackSelection
import com.lambda.interaction.inventory.container.Container
import com.lambda.interaction.managers.inventory.InvRequestBuilder
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SingleStackInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.PlayerScreenHandler
import net.minecraft.screen.slot.Slot

data object CreativeContainer : Container(Rank.Creative) {
	override val slots = emptyList<Slot>()
	override var stacks = emptyList<ItemStack>()

	override val swapMethodPriority = 11

	override val description = buildText { literal("Creative") }

	context(safeContext: SafeContext)
	override fun InvRequestBuilder.transfer(fromHere: Slot, toSlot: Slot) {
		clickCreativeStack(fromHere.stack, toSlot.id)
		safeContext.player.currentScreenHandler.slots.getOrNull(toSlot.id)?.stack = fromHere.stack
	}

	override fun getSlot(selection: StackSelection) =
		selection.optimalStack?.let { stack ->
			Slot(
				object : SingleStackInventory {
					override fun getStack() = stack
					override fun setStack(stack: ItemStack?) {}
					override fun markDirty() {}
					override fun canPlayerUse(player: PlayerEntity?) = false
				},
				0, 0, 0
			)
		}

	override fun stackCount(selection: StackSelection): Int =
		if (mc.player?.isCreative == true && correctScreenHandler && selection.optimalStack != null) Int.MAX_VALUE else -1

	override fun spaceAvailable(selection: StackSelection): Int =
		if (mc.player?.isCreative == true && correctScreenHandler && selection.optimalStack != null) Int.MAX_VALUE else -1

	private val correctScreenHandler
		get() = mc.player?.currentScreenHandler is PlayerScreenHandler || mc.player?.currentScreenHandler is CreativeInventoryScreen.CreativeScreenHandler
}
