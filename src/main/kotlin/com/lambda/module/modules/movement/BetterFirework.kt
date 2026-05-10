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

package com.lambda.module.modules.movement

import com.lambda.Lambda
import com.lambda.Lambda.mc
import com.lambda.config.applyEdits
import com.lambda.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.settings.complex.Bind
import com.lambda.config.settings.complex.KeybindSetting.Companion.onPress
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.hotbar.HotbarRequest
import com.lambda.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.CommunicationUtils.warn
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.player.SlotUtils.hotbarAndInventoryStacks
import com.lambda.util.player.SlotUtils.hotbarStacks
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.HitResult

object BetterFirework : Module(
	name = "BetterFirework",
	description = "Automatic takeoff with fireworks",
	tag = ModuleTag.Movement,
) {
	private var activateButton: Bind by setting("Activate Key", Bind(0, 0, Mouse.Middle.ordinal), "Button to activate Firework")
		.onPress {
			if (mc.crosshairTarget?.type == HitResult.Type.BLOCK &&
				!middleClickCancel &&
				activateButton.mouse == Lambda.mc.options.pickItemKey.boundKey.code) return@onPress

			if (!player.isElytraEquipped) {
				warn("You need to equip an elytra to use this module!")
				return@onPress
			}
			if (!player.hasFireworks) {
				warn("You need to have fireworks in your inventory to use this module!")
				return@onPress
			}
			// Prevent using multiple times
			if (takeoffState != TakeoffState.None) return@onPress
			// If already gliding use another firework
			if (player.canOpenElytra || player.isGliding) takeoffState = TakeoffState.StartFlying
			else if (player.canTakeoff) takeoffState = TakeoffState.Jumping
		}
	@Suppress("unused")
	private var midFlightActivationKey by setting("Mid-Flight Activation Key", Bind.EMPTY, "Firework use key for mid flight activation")
		.onPress { if (player.isGliding) takeoffState = TakeoffState.StartFlying }
	private var middleClickCancel by setting("Middle Click Cancel", false, description = "Cancel pick block action on middle mouse click") { activateButton.key != KeyCode.Unbound.code }
	private var fireworkInteract by setting("Right Click Fly", true, "Automatically start flying when right clicking fireworks")
	private var fireworkInteractCancel by setting("Right Click Cancel", false, "Cancel block interactions while holding fireworks") { fireworkInteract }

	private var clientSwing by setting("Swing", true, "Swing hand client side")
	private var invUse by setting("Inventory", true, "Use fireworks from inventory") { activateButton.key != KeyCode.Unbound.code }

	private var takeoffState = TakeoffState.None

	val ClientPlayerEntity.isElytraEquipped: Boolean
		get() = inventory.equipment.get(EquipmentSlot.CHEST)?.item == Items.ELYTRA

	val ClientPlayerEntity.hasFireworks: Boolean
		get() = selectStack { isItem(Items.FIREWORK_ROCKET) }
			.filterStacks(inventory.mainStacks)
			.isNotEmpty() || offHandStack.item == Items.FIREWORK_ROCKET

	val ClientPlayerEntity.canTakeoff: Boolean
		get() = (isOnGround || canOpenElytra) && isElytraEquipped && hasFireworks

	val ClientPlayerEntity.canOpenElytra: Boolean
		get() = !abilities.flying && !isClimbing && !isGliding && !isTouchingWater && !isOnGround && !hasVehicle() && !hasStatusEffect(StatusEffects.LEVITATION)

	init {
		setModulePriority(1)
		setDefaultAutomationConfig {
			applyEdits {
				hideAllBlocksExcept(::hotbarConfig, ::inventoryConfig)
				hotbarConfig::tickStageMask.edit { defaultValue(mutableSetOf(TickEvent.Pre)) }
				inventoryConfig::tickStageMask.edit { defaultValue(mutableSetOf(TickEvent.Pre)) }
			}
		}

		listen<TickEvent.Pre> {
			when (takeoffState) {
				TakeoffState.None -> {}

				TakeoffState.Jumping -> {
					player.jump()
					takeoffState = TakeoffState.StartFlying
				}

				TakeoffState.StartFlying -> {
					if (player.canOpenElytra) {
						player.startGliding()
						connection.sendPacket(ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING))
					}
					startFirework(invUse)
					takeoffState = TakeoffState.None
				}
			}
		}
	}

	/**
	 * Returns true if the mc item interaction should be canceled
	 */
	@JvmStatic
	fun onInteract() =
		runSafe {
			when {
				!fireworkInteract ||
						player.inventory.selectedStack?.item != Items.FIREWORK_ROCKET ||
						player.isGliding || // No need to do special magic if we are already holding fireworks and flying
						(mc.crosshairTarget != null && mc.crosshairTarget?.type != HitResult.Type.MISS && !fireworkInteractCancel) -> false
				else -> {
					mc.itemUseCooldown += 4
					val cancelInteract = player.canTakeoff || fireworkInteractCancel
					if (player.canTakeoff) {
						takeoffState = TakeoffState.Jumping
					} else if (player.canOpenElytra) {
						takeoffState = TakeoffState.StartFlying
					}
					cancelInteract
				}
			}
		} ?: false

	/**
	 * Returns true if the pick interaction should be canceled.
	 */
	@JvmStatic
	fun onPick() = if (activateButton.mouse == mc.options.pickItemKey.boundKey.code) middleClickCancel else false

	fun SafeContext.sendSwing() {
		if (clientSwing) player.swingHand(Hand.MAIN_HAND)
		else connection.sendPacket(HandSwingC2SPacket(Hand.MAIN_HAND))
	}

	/**
	 * Use a firework from the hotbar or inventory if possible.
	 * Return true if a firework has been used
	 */
	fun SafeContext.startFirework(inventory: Boolean) {
		val stack = selectStack(count = 1) { isItem(Items.FIREWORK_ROCKET) }

		stack.bestItemMatch(player.hotbarStacks)
			?.let {
				val request = HotbarRequest(player.hotbarStacks.indexOf(it), this@BetterFirework, keepTicks = 0)
					.submit(queueIfMismatchedStage = false)
				if (request.done) {
					interaction.interactItem(player, Hand.MAIN_HAND)
					sendSwing()
				}
				return
			}

		if (!inventory) return

		stack.bestItemMatch(player.hotbarAndInventoryStacks)
			?.let {
				val swapSlotId = player.hotbarAndInventoryStacks.indexOf(it)
				val hotbarSlotToSwapWith = player.hotbarStacks.find { slot -> slot.isEmpty }?.let { slot -> player.hotbarStacks.indexOf(slot) } ?: 8

				inventoryRequest {
					swap(swapSlotId, hotbarSlotToSwapWith)
					action {
						val request = HotbarRequest(hotbarSlotToSwapWith, this@BetterFirework, keepTicks = 0, nowOrNothing = true)
							.submit(queueIfMismatchedStage = false)
						if (request.done) {
							interaction.interactItem(player, Hand.MAIN_HAND)
							sendSwing()
						}
					}
					swap(swapSlotId, hotbarSlotToSwapWith)
				}.submit()
			}
	}

	enum class TakeoffState {
		None,
		Jumping,
		StartFlying
	}
}
