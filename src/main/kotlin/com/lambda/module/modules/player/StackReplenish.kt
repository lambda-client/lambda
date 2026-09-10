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
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.container.containers.HotbarContainer
import com.lambda.interaction.container.containers.InventoryContainer
import com.lambda.interaction.manager.managers.inventory.InvRequestBuilder.Companion.inventoryRequest
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import com.lambda.util.item.ItemStackUtils.slotId
import net.minecraft.item.ItemStack
import net.minecraft.item.Items

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
			HotbarContainer.stacks.forEach { stack -> checkReplenish(stack) }
			if (offhand) checkReplenish(player.offHandStack)
		}
	}

	private fun SafeContext.checkReplenish(stack: ItemStack) {
		if (stack.count.toFloat() / stack.maxCount >= (minStackPercent.toFloat() / 100)) return
		if (!stack.isStackable) return

		InventoryContainer.stacks.forEach { invStack ->
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