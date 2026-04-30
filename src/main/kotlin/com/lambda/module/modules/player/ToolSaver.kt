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

import com.lambda.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.applyEdits
import com.lambda.event.events.ContainerEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.interaction.material.container.containers.HotbarContainer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.EnchantmentUtils.forEachEnchantment
import com.lambda.util.EnchantmentUtils.getEnchantment
import com.lambda.util.player.SlotUtils.hotbarSlots
import com.lambda.util.player.SlotUtils.inventorySlots
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot

@Suppress("unused")
object ToolSaver : Module(
	name = "ToolSaver",
	description = "Moves tools from your hotbar into your inventory when they get too damaged",
	ModuleTag.PLAYER
) {
	private val minDurabilityPercentage by setting("Min Durability", 5, 0..100, 1, "Minimum durability percentage before being swapped for a new piece", "%")
	private val replace by setting("Replace", true, "Replaces the tool with the one of the same kind")

	init {
		setDefaultAutomationConfig {
			applyEdits {
				hideAllGroupsExcept(inventoryConfig)
			}
		}

		listen<TickEvent.Pre> {
			val endangeredStacks = player.hotbarSlots.filter { it.stack.isEndangered }

			val inventorySlots = player.inventorySlots
			val swaps = endangeredStacks
				.mapNotNull { endangered ->
					val sorter = compareByDescending<Slot> { swapSlot ->
						if (!replace) 0
						else swapSlot.stack.item == endangered.stack.item
					}.thenByDescending { swapSlot ->
						if (!replace) return@thenByDescending 0
						var matchingEnchantments = 0
						endangered.stack.forEachEnchantment { enchantment, level ->
							val swapEnchantmentLevel = swapSlot.stack.getEnchantment(enchantment)
							if (swapEnchantmentLevel != 0 && swapEnchantmentLevel == level) matchingEnchantments++
						}
						matchingEnchantments
					}.thenByDescending {
						it.stack.isEmpty
					}.thenByDescending {
						it.stack.item in inventoryConfig.disposables
					}.thenByDescending {
						it.stack.isStackable
					}
					val swapWith = inventorySlots
						.filter { !it.stack.isEndangered }
						.sortedWith(sorter)
						.firstOrNull()
						?: return@mapNotNull null
					endangered to swapWith
				}

			if (swaps.isEmpty()) return@listen

			inventoryRequest {
				swaps.forEach {
					pickup(it.first.id)
					pickup(it.second.id)
					if (!it.second.stack.isEmpty)
						pickup(it.first.id)
				}
			}.submit()
		}

		listen<ContainerEvent.Transfer> { event ->
			if (event.to is HotbarContainer && event.fromSlot.stack.isEndangered) event.cancel()
			else if (event.from is HotbarContainer && event.toSlot.stack.isEndangered) event.cancel()
		}
	}

	private val ItemStack.isEndangered get() =
		isDamageable && 1 - (damage.toFloat() / maxDamage) < minDurabilityPercentage.toFloat() / 100
}