
package com.minato.interaction.handlers

import com.minato.context.SafeContext
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.material.StackSelection.Companion.selectStack
import com.minato.module.modules.combat.AutoArmor
import com.minato.module.modules.movement.elytrafly.ElytraFly
import com.minato.module.modules.movement.elytrafly.ElytraFly.mode
import com.minato.module.modules.player.AutoElytraSwap
import com.minato.module.modules.player.AutoElytraSwap.elytraFlyOnly
import com.minato.module.modules.player.AutoElytraSwap.glideDelay
import com.minato.util.EnchantmentUtils.getEnchantment
import com.minato.util.player.PlayerUtils.canGlideWithChestPiece
import com.minato.util.player.PlayerUtils.canStartGliding
import com.minato.util.player.SlotUtils.hotbarAndInventorySlots
import net.minecraft.component.DataComponentTypes
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.registry.tag.ItemTags

object GlideHandler {
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
		get() = (AutoElytraSwap.isEnabled && !elytraFlyOnly) || ElytraFly.isEnabled
	private val autoSwapChecking
		get() = AutoElytraSwap.isEnabled && (!elytraFlyOnly || ElytraFly.isEnabled)

	var swapped = false
	var manuallySwapped = false
	private val pendingGlides = mutableListOf<PendingGlideAction>()

	init {
		listen<TickEvent.Pre>({ -1000 }) {
			if (!player.isGliding && pendingGlides.isEmpty()) AutoElytraSwap.restore()
			tickPendingGlides()
		}

		listen<TickEvent.Player.Pre> {
			if (!overridingGlide) return@listen
			if (player.isGliding || pendingGlides.isNotEmpty()) return@listen

			val preJump = player.input?.playerInput?.jump ?: false
			val postJump = mc.options.jumpKey.isPressed

			if (postJump && !preJump && player.canStartGliding) onGlide()
		}
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
		if (!autoSwapChecking || readyToGlide) return readyToGlide

		swapped = true

		AutoArmor.overriddenElytraPriority = elytra
		if (AutoArmor.isEnabled)  {
			with(AutoArmor) { tick() }
			return elytra == player.canGlideWithChestPiece()
		}

		manuallySwapped = AutoElytraSwap.manualSwap(elytra)
		return manuallySwapped
	}

	@JvmStatic
	fun registerOnGlide(delay: Int = 0, block: SafeContext.() -> Boolean) {
		pendingGlides.add(PendingGlideAction(delay, block))
	}

	@JvmStatic
	context(safeContext: SafeContext)
	fun canGlide(): Boolean =
		with(safeContext) {
			val fakeFly = ElytraFly.isEnabled && ElytraFly.fakeFly
			val canGlideAlready = player.canGlideWithChestPiece() != fakeFly
			if (!autoSwapChecking || canGlideAlready) return canGlideAlready
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