
package com.minato.interaction.material.container.containers

import com.minato.context.SafeContext
import com.minato.interaction.managers.inventory.InventoryRequest
import com.minato.interaction.material.StackSelection
import com.minato.interaction.material.container.MaterialContainer
import com.minato.util.text.buildText
import com.minato.util.text.literal
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SingleStackInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.PlayerScreenHandler
import net.minecraft.screen.slot.Slot

data object CreativeContainer : MaterialContainer(Rank.Creative) {
	context(_: SafeContext)
	override val slots: List<Slot>
		get() = emptyList()
	override var stacks = emptyList<ItemStack>()

	override val swapMethodPriority = 11

	override val description = buildText { literal("Creative") }

	context(safeContext : SafeContext)
	override fun InventoryRequest.InvRequestBuilder.transfer(fromHere: Slot, toSlot: Slot) {
		clickCreativeStack(fromHere.stack, toSlot.id)
		safeContext.player.currentScreenHandler.slots[toSlot.id].stack = fromHere.stack
	}

	context(safeContext: SafeContext)
	override fun getSlot(stackSelection: StackSelection) =
		stackSelection.optimalStack?.let { stack ->
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

	context(safeContext: SafeContext)
	override fun materialAvailable(selection: StackSelection): Int =
		if (safeContext.player.isCreative && correctScreenHandler && selection.optimalStack != null) Int.MAX_VALUE else -1

	context(safeContext: SafeContext)
	override fun spaceAvailable(selection: StackSelection): Int =
		if (safeContext.player.isCreative && correctScreenHandler && selection.optimalStack != null) Int.MAX_VALUE else -1

	context(safeContext: SafeContext)
	private val correctScreenHandler
		get() = safeContext.player.currentScreenHandler is PlayerScreenHandler || safeContext.player.currentScreenHandler is CreativeInventoryScreen.CreativeScreenHandler
}
