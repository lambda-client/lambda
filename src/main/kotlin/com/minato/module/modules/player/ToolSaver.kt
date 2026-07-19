
package com.minato.module.modules.player

import com.minato.config.ConfigEditor.hideAllExcept
import com.minato.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.minato.config.withEdits
import com.minato.event.events.ContainerEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.minato.interaction.material.container.containers.HotbarContainer
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.EnchantmentUtils.forEachEnchantment
import com.minato.util.EnchantmentUtils.getEnchantment
import com.minato.util.player.SlotUtils.hotbarSlots
import com.minato.util.player.SlotUtils.inventorySlots
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
		setDefaultAutomationConfig()
			.withEdits {
				hideAllExcept(::inventoryConfig)
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