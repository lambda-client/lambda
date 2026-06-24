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
import com.lambda.module.modules.movement.elytrafly.ElytraFly.mode
import com.lambda.module.tag.ModuleTag
import com.lambda.util.CommunicationUtils.warn
import com.lambda.util.EnchantmentUtils.getEnchantment
import com.lambda.util.player.PlayerUtils.canStartGliding
import com.lambda.util.player.SlotUtils.armorSlots
import com.lambda.util.player.SlotUtils.hotbarAndInventorySlots
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.component.DataComponentTypes
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.registry.tag.ItemTags
import net.minecraft.screen.slot.Slot

object AutoElytraSwap : Module(
	name = "AutoElytraSwap",
	description = "Automatically equips an elytra when attempting to glide, and removes it afterward",
	tag = ModuleTag.PLAYER
) {
	@JvmStatic val elytraFlyOnly by setting("ElytraFly Only", false, "Only swaps the chest piece when the ElytraFly module is enabled and gliding")
	private val glideDelay by setting("Glide Delay", 0, 0..20, 1, "The delay, in ticks, between swapping to elytra, and starting to glide")

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

	@JvmStatic val overridingGlide
		get() = (isEnabled && !elytraFlyOnly) || ElytraFly.isEnabled
	private val activeSwapChecking
		get() = isEnabled && (!elytraFlyOnly || ElytraFly.isEnabled)

	private var swapped = false
	private var manuallySwapped = false
	private var prevGliding = false
	private val pendingGlides = mutableListOf<PendingGlideAction>()

	init {
		listen<TickEvent.Pre>({ -1000 }, alwaysListen = true) {
			if (!player.isGliding && pendingGlides.isEmpty()) reset()
			prevGliding = player.isGliding
			tickPendingGlides()
		}

		listen<TickEvent.Player.Pre>(alwaysListen = true) {
			if (!overridingGlide) return@listen
			if (player.isGliding || pendingGlides.isNotEmpty()) return@listen

			val preJump = player.input?.playerInput?.jump ?: false
			val postJump = mc.options.jumpKey.isPressed

			if (postJump && !preJump && player.canStartGliding) onGlide()
		}

		onDisable { reset() }
	}

	context(safeContext: SafeContext)
	fun onGlide() {
		if (!safeContext.prepForGlide()) return
		val delay = if (swapped) glideDelay else 0
		if (ElytraFly.isEnabled) {
			val elytraFly = mode.elytraFly
			registerOnGlide(delay) { with(elytraFly) { flyOrFakeFly() } }
		} else {
			registerOnGlide(delay) {
				if (!player.canStartGliding) return@registerOnGlide false
				player.startGliding()
				connection.sendPacket(ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING))
				true
			}
		}

		safeContext.tickPendingGlides()
	}

	private fun SafeContext.tickPendingGlides() {
		val iterator = pendingGlides.iterator()
		while (iterator.hasNext()) {
			val next = iterator.next()
			if (next.delay <= 0) {
				if (!next.block(this)) {
					pendingGlides.clear()
					break
				} else iterator.remove()
			} else {
				next.delay--
				break
			}
		}
	}

	private fun SafeContext.prepForGlide(): Boolean {
		val elytra = ElytraFly.isDisabled || !ElytraFly.fakeFly
		val readyToGlide = player.canGlideWithChestPiece() == elytra
		if (!activeSwapChecking || readyToGlide) return readyToGlide

		swapped = true

		AutoArmor.overriddenElytraPriority = elytra
		if (AutoArmor.isEnabled)  {
			with(AutoArmor) { tick() }
			return elytra == player.canGlideWithChestPiece()
		}

		manuallySwapped = manualSwap(elytra)
		return manuallySwapped
	}

	private fun SafeContext.manualSwap(elytra: Boolean): Boolean {
		val swapSlot =
			player.hotbarAndInventorySlots.let { slots ->
				if (elytra) ELYTRA_SELECTION.filterSlots(slots)
				else CHESTPLATE_SELECTION.filterSlots(slots)
			}.minByOrNull { it.index } ?: run {
				AutoElytraSwap.warn("The required armor piece was not found for AutoElytraSwap to work.")
				return false
			}

		return swapWithChestplate(swapSlot)
	}

	private fun SafeContext.swapWithChestplate(swapSlot: Slot): Boolean {
		val chestplateSlot = player.armorSlots[1]
		return inventoryRequest {
			if (swapSlot.index in 0..8) swap(chestplateSlot.id, swapSlot.index)
			else {
				moveSlot(swapSlot.id, chestplateSlot.id)
				if (!chestplateSlot.stack.isEmpty) pickup(swapSlot.id)
			}
		}.submit(false).done
	}

	private fun SafeContext.reset() {
		AutoArmor.overriddenElytraPriority = null
		swapped = false
		if (!manuallySwapped) return
		val hasElytra = player.canGlideWithChestPiece()
		manualSwap(!hasElytra)
		manuallySwapped = false
	}

	private fun ClientPlayerEntity.canGlideWithChestPiece() =
		LivingEntity.canGlideWith(getEquippedStack(EquipmentSlot.CHEST), EquipmentSlot.CHEST)

	@JvmStatic
	fun registerOnGlide(delay: Int = 0, block: SafeContext.() -> Boolean) {
		pendingGlides.add(PendingGlideAction(delay, block))
	}

	context(safeContext: SafeContext)
	@JvmStatic
	fun canGlide(): Boolean =
		with(safeContext) {
			val fakeFly = ElytraFly.isEnabled && ElytraFly.fakeFly
			val canGlideAlready = player.canGlideWithChestPiece() != fakeFly
			if (!activeSwapChecking || canGlideAlready) return canGlideAlready
			return player.hotbarAndInventorySlots.let { slots ->
				if (fakeFly) CHESTPLATE_SELECTION.filterSlots(slots).isNotEmpty()
				else ELYTRA_SELECTION.filterSlots(slots).isNotEmpty()
			}
		}

	private class PendingGlideAction(
		var delay: Int,
		val block: SafeContext.() -> Boolean
	)
}