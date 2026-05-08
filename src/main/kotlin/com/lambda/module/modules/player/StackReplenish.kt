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

import com.lambda.config.applyEdits
import com.lambda.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.item.ItemStackUtils.slotId
import com.lambda.util.player.SlotUtils.hotbarStacks
import com.lambda.util.player.SlotUtils.inventoryStacks
import net.minecraft.item.ItemStack
import net.minecraft.item.Items

@Suppress("unused")
object StackReplenish : Module(
	name = "StackReplenish",
	description = "Automatically refills stacks from your inventory",
	tag = ModuleTag.Player
) {
	private val minStackPercent by setting("Min Stack Percentage", 30, 0..100, 1, "Minimum percentage of a complete stack before refilling", "%")
	private val offhand by setting("Offhand", false, "Replenishes the players offhand stack")

	init {
		setDefaultAutomationConfig {
			applyEdits {
				hideAllBlocksExcept(inventoryConfig)
				inventoryConfig.apply {
					hide(::disposables, ::swapWithDisposables, ::providerPriority, ::storePriority)
				}
			}
		}

		listen<TickEvent.Pre> {
			if (player.currentScreenHandler.cursorStack.item !== Items.AIR) return@listen
			player.hotbarStacks.forEach { stack -> checkReplenish(stack) }
			if (offhand) checkReplenish(player.offHandStack)
		}
	}

	private fun SafeContext.checkReplenish(stack: ItemStack) {
		if (stack.count.toFloat() / stack.maxCount >= (minStackPercent.toFloat() / 100)) return
		if (!stack.isStackable) return

		player.inventoryStacks.forEach { invStack ->
			if (!ItemStack.areItemsAndComponentsEqual(invStack, stack)) return@forEach
			val invId = invStack.slotId
			val completing = stack.count + invStack.count >= stack.maxCount
			val tooMany = invStack.count + stack.count > stack.maxCount
			inventoryRequest {
				moveSlot(invId, stack.slotId)
				if (tooMany) pickup(invId)
			}.submit()
			if (completing) return
		}
	}
}