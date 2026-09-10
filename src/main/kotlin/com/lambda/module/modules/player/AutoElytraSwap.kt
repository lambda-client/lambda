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

import com.lambda.context.SafeContext
import com.lambda.interaction.container.containers.ArmorContainer
import com.lambda.interaction.handler.handlers.GlideHandler.CHESTPLATE_SELECTION
import com.lambda.interaction.handler.handlers.GlideHandler.ELYTRA_SELECTION
import com.lambda.interaction.handler.handlers.GlideHandler.manuallySwapped
import com.lambda.interaction.handler.handlers.GlideHandler.swapped
import com.lambda.interaction.handler.handlers.findSlots
import com.lambda.interaction.manager.managers.inventory.InvRequestBuilder.Companion.inventoryRequest
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import com.lambda.module.modules.combat.AutoArmor
import com.lambda.util.CommunicationUtils.warn
import com.lambda.util.player.PlayerUtils.canGlideWithChestPiece
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

	fun manualSwap(elytra: Boolean): Boolean {
		val swapSlot =
			findSlots(
				if (elytra) ELYTRA_SELECTION
				else CHESTPLATE_SELECTION
			).minByOrNull { it.index }
				?: run {
					AutoElytraSwap.warn("The required armor piece was not found for AutoElytraSwap to work.")
					return false
				}

		return swapWithChestplate(swapSlot)
	}

	private fun swapWithChestplate(swapSlot: Slot): Boolean {
		val chestplateSlot = ArmorContainer.slots.getOrNull(1) ?: return false
		return AutoElytraSwap.inventoryRequest {
			if (swapSlot.index in 0..8) swapWithHotbar(chestplateSlot.id, swapSlot.index)
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