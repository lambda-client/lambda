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

package com.lambda.module.modules.player

import com.lambda.config.automation.setDefaultAutomationConfig
import com.lambda.config.hide
import com.lambda.config.hideAllExcept
import com.lambda.config.withEdits
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.container.containers.HotbarContainer
import com.lambda.interaction.container.containers.InventoryContainer
import com.lambda.interaction.container.containers.OffhandContainer
import com.lambda.interaction.container.selection.StackSelectionBuilder.Companion.stackSelection
import com.lambda.interaction.container.selection.select
import com.lambda.interaction.handler.handlers.findSlots
import com.lambda.interaction.manager.managers.inventory.InvRequestBuilder.Companion.inventoryRequest
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.slot.Slot

@Suppress("unused")
object StackReplenish : Module(
	name = "StackReplenish",
	description = "Automatically refills stacks from your inventory",
	tag = ModuleTag.PLAYER
) {
	private val minStackPercent by setting("Min Stack Percentage", 30, 0..100, 1, "Minimum percentage of a complete stack before refilling", "%")
	private val offhand by setting("Offhand", false, "Replenishes the players offhand stack")

	init {
		setDefaultAutomationConfig()
			.withEdits {
				hideAllExcept(::inventoryConfig)
				inventoryConfig.apply {
					hide(::disposables)
				}
			}

		listen<TickEvent.Pre> {
			if (player.currentScreenHandler.cursorStack.item !== Items.AIR) return@listen
			findSlots(containerSelection = HotbarContainer.select()).forEach { slot -> checkReplenish(slot) }
			if (offhand) findSlots(containerSelection = OffhandContainer.select()).forEach { slot -> checkReplenish(slot) }
		}
	}

	private fun checkReplenish(targetSlot: Slot) {
		val stack = targetSlot.stack
		if (stack.isEmpty || !stack.isStackable) return
		if (stack.count.toFloat() / stack.maxCount >= (minStackPercent.toFloat() / 100)) return

		val replenishSlots =
			findSlots(
				stackSelection {
					predicate { s, _ -> ItemStack.areItemsAndComponentsEqual(s, stack) }
				},
				InventoryContainer.select()
			)

		replenishSlots.forEach { replenishSlot ->
			val invStack = replenishSlot.stack
			val completing = targetSlot.stack.count + invStack.count >= stack.maxCount
			val tooMany = targetSlot.stack.count + invStack.count > stack.maxCount
			inventoryRequest {
				moveSlot(replenishSlot.id, targetSlot.id)
				if (tooMany) pickup(replenishSlot.id)
			}.submit()
			if (completing) return
		}
	}
}