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
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.module.Module
import com.lambda.module.modules.combat.AutoArmor
import com.lambda.module.modules.movement.elytrafly.ElytraFly
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.CommunicationUtils.warn
import com.lambda.util.EnchantmentUtils.getEnchantment
import com.lambda.util.player.SlotUtils.armorSlots
import com.lambda.util.player.SlotUtils.hotbarAndInventorySlots
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.component.DataComponentTypes
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.item.Items
import net.minecraft.registry.tag.ItemTags
import net.minecraft.screen.slot.Slot

object AutoElytraSwap : Module(
	name = "AutoElytraSwap",
	description = "Automatically equips an elytra when attempting to glide, and removes it afterward",
	tag = ModuleTag.PLAYER
) {
	val ELYTRA_SELECTION =
		selectStack {
			sortByDescending {
				it.getEnchantment(Enchantments.UNBREAKING)
			}.thenByDescending {
				it.getEnchantment(Enchantments.MENDING)
			}
			isItem(Items.ELYTRA)
				.and { it.damage < it.maxDamage }
		}
	val CHESTPLATE_SELECTION =
		selectStack {
			sortByDescending {
				it.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, null)
					?.modifiers
					?.find { modifier -> modifier.attribute == EntityAttributes.ARMOR }
					?.modifier?.value
					?: 0.0
			}.thenByDescending {
				it.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, null)
					?.modifiers
					?.find { modifier -> modifier.attribute == EntityAttributes.ARMOR_TOUGHNESS }
					?.modifier?.value
					?: 0.0
			}.thenByDescending {
				it.getEnchantment(Enchantments.UNBREAKING)
			}.thenByDescending {
				it.getEnchantment(Enchantments.MENDING)
			}
			hasTag(ItemTags.CHEST_ARMOR)
				.and { it.item != Items.ELYTRA }
				.and { it.damage < it.maxDamage }
		}

	private var manuallySwapped = false
	@JvmStatic var pendingGlides = mutableListOf<SafeContext.() -> Unit>()

	init {
		listen<TickEvent.Pre>({ -1000 }) {
			if (!player.isGliding) reset()
		}

		listen<TickEvent.Player.Post>(alwaysListen = true) {
			pendingGlides.forEach { it() }
			pendingGlides.clear()
		}
	}

	@JvmStatic
	fun SafeContext.onGlide(): Boolean {
		val elytra = ElytraFly.isDisabled || !ElytraFly.fakeFly
		AutoArmor.overriddenElytraPriority = elytra
		if (AutoArmor.isEnabled)  {
			with(AutoArmor) { tick() }
			return player.canGlideWithChestPiece()
		}

		if (player.canGlideWithChestPiece() == elytra) return true
		val swapped = swap(elytra)
		if (swapped) manuallySwapped = true
		return swapped
	}

	private fun SafeContext.swap(elytra: Boolean): Boolean {
		val swapSlot =
			player.hotbarAndInventorySlots.let { slots ->
				if (elytra) ELYTRA_SELECTION.filterSlots(slots)
				else CHESTPLATE_SELECTION.filterSlots(slots)
			}.minByOrNull { it.index } ?: run {
				AutoElytraSwap.warn("The required armor piece was not found for AutoElytraSwap to work.")
				return false
			}

		swapWithChestplate(swapSlot)
		return true
	}

	private fun SafeContext.swapWithChestplate(swapSlot: Slot) {
		val chestplateSlot = player.armorSlots[1]
		inventoryRequest {
			if (swapSlot.index in 0..8) swap(chestplateSlot.id, swapSlot.index)
			else {
				moveSlot(swapSlot.id, chestplateSlot.id)
				if (!chestplateSlot.stack.isEmpty) pickup(swapSlot.id)
			}
		}.submit()
	}

	private fun SafeContext.reset() {
		AutoArmor.overriddenElytraPriority = null
		if (!manuallySwapped) return
		val hasElytra = player.canGlideWithChestPiece()
		swap(!hasElytra)
		manuallySwapped = false
	}

	private fun ClientPlayerEntity.canGlideWithChestPiece() =
		LivingEntity.canGlideWith(getEquippedStack(EquipmentSlot.CHEST), EquipmentSlot.CHEST)

	@JvmStatic
	fun registerOnGlide(block: SafeContext.() -> Unit) {
		pendingGlides.add(block)
	}
}