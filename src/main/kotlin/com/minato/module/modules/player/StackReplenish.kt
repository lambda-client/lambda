
package com.minato.module.modules.player

import com.minato.config.ConfigEditor.hide
import com.minato.config.ConfigEditor.hideAllExcept
import com.minato.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.minato.config.withEdits
import com.minato.context.SafeContext
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.item.ItemStackUtils.slotId
import com.minato.util.player.SlotUtils.hotbarStacks
import com.minato.util.player.SlotUtils.inventoryStacks
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
					hide(::disposables, ::swapWithDisposables, ::providerPriority, ::storePriority)
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