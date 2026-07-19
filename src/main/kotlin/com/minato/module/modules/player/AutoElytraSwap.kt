
package com.minato.module.modules.player

import com.minato.context.SafeContext
import com.minato.interaction.handlers.GlideHandler.CHESTPLATE_SELECTION
import com.minato.interaction.handlers.GlideHandler.ELYTRA_SELECTION
import com.minato.interaction.handlers.GlideHandler.manuallySwapped
import com.minato.interaction.handlers.GlideHandler.swapped
import com.minato.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.minato.module.Module
import com.minato.module.modules.combat.AutoArmor
import com.minato.module.tag.ModuleTag
import com.minato.util.CommunicationUtils.warn
import com.minato.util.player.PlayerUtils.canGlideWithChestPiece
import com.minato.util.player.SlotUtils.armorSlots
import com.minato.util.player.SlotUtils.hotbarAndInventorySlots
import net.minecraft.screen.slot.Slot

object AutoElytraSwap : Module(
	name = "AutoElytraSwap",
	description = "Automatically equips an elytra when attempting to glide, and removes it afterward",
	tag = ModuleTag.PLAYER
) {
	@JvmStatic val elytraFlyOnly by setting("ElytraFly Only", false, "Only swaps the chest piece when the ElytraFly module is enabled and gliding")
	val glideDelay by setting("Glide Delay", 0, 0..20, 1, "The delay, in ticks, between swapping to elytra, and starting to glide", unit = " ticks")

	init {
		onDisable { restore() }
	}

	context(safeContext: SafeContext)
	fun manualSwap(elytra: Boolean): Boolean {
		val swapSlot =
			safeContext.player.hotbarAndInventorySlots.let { slots ->
				if (elytra) ELYTRA_SELECTION.filterSlots(slots)
				else CHESTPLATE_SELECTION.filterSlots(slots)
			}.minByOrNull { it.index } ?: run {
				AutoElytraSwap.warn("The required armor piece was not found for AutoElytraSwap to work.")
				return false
			}

		return safeContext.swapWithChestplate(swapSlot)
	}

	private fun SafeContext.swapWithChestplate(swapSlot: Slot): Boolean {
		val chestplateSlot = player.armorSlots[1]
		return AutoElytraSwap.inventoryRequest {
			if (swapSlot.index in 0..8) swap(chestplateSlot.id, swapSlot.index)
			else {
				moveSlot(swapSlot.id, chestplateSlot.id)
				if (!chestplateSlot.stack.isEmpty) pickup(swapSlot.id)
			}
		}.submit(false).done
	}

	context(safeContext: SafeContext)
	fun restore() {
		AutoArmor.overriddenElytraPriority = null
		swapped = false
		if (!manuallySwapped) return
		val hasElytra = safeContext.player.canGlideWithChestPiece()
		manualSwap(!hasElytra)
		manuallySwapped = false
	}
}