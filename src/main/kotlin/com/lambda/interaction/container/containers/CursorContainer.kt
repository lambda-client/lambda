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

package com.lambda.interaction.container.containers

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.interaction.container.Container
import com.lambda.interaction.container.ContainerType
import com.lambda.interaction.manager.managers.inventory.InvRequestBuilder
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SingleStackInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot

object CursorContainer : Container(ContainerType.Cursor) {
	override val slots: List<Slot>
		get() =
			mc.player?.currentScreenHandler?.let {
				listOf(CursorSlot(it))
			} ?: emptyList()

	override var stacks: List<ItemStack>
		get() =
			mc.player?.currentScreenHandler?.cursorStack?.let {
				listOf(it)
			} ?: emptyList()
		set(_) {}

	override val swapMethodPriority = 12

	override val description = buildText { literal("Cursor") }

	context(safeContext: SafeContext)
	override fun InvRequestBuilder.swap(fromHere: Slot, toSlot: Slot) {
		pickup(toSlot.id, 0)
	}

	class CursorSlot(val screenHandler: ScreenHandler) : Slot(
		object : SingleStackInventory {
			override fun getStack(): ItemStack = screenHandler.cursorStack
			override fun setStack(stack: ItemStack?) {
				if (stack != null) screenHandler.cursorStack = stack
			}
			override fun markDirty() {}
			override fun canPlayerUse(player: PlayerEntity?) = true
		},
		0, 0, 0
	) {
		override fun canInsert(stack: ItemStack): Boolean = true
		override fun canTakeItems(playerEntity: PlayerEntity?): Boolean = true
	}
}
