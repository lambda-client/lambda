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
import com.lambda.config.ConfigEditor.editSetting
import com.lambda.config.ConfigEditor.hideAllExcept
import com.lambda.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.settings.complex.Bind
import com.lambda.config.settings.complex.KeybindSetting.Companion.onPress
import com.lambda.config.withEdits
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.handler.handlers.GlideHandler
import com.lambda.interaction.inventory.StackSelectionBuilder.Companion.select
import com.lambda.interaction.inventory.StackSelectionBuilder.Companion.selectStack
import com.lambda.interaction.inventory.container.containers.HotbarAndInventoryContainer
import com.lambda.interaction.inventory.container.containers.HotbarContainer
import com.lambda.interaction.inventory.container.containers.InventoryContainer
import com.lambda.interaction.manager.managers.hotbar.HotbarRequestBuilder.Companion.hotbarRequest
import com.lambda.interaction.manager.managers.inventory.InvRequestBuilder.Companion.inventoryRequest
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.CommunicationUtils.warn
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.player.PlayerUtils
import com.lambda.util.player.PlayerUtils.canStartGliding
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.HitResult

object BetterFirework : Module(
	name = "BetterFirework",
	description = "Automatic takeoff with fireworks",
	tag = ModuleTag.MOVEMENT,
	modulePriority = 1
) {
	private var activateButton: Bind by setting("Activate Key", Bind(0, 0, Mouse.Middle.ordinal), "Button to activate Firework")
		.onPress {
			if (mc.crosshairTarget?.type == HitResult.Type.BLOCK &&
				!middleClickCancel &&
				activateButton.mouse == Lambda.mc.options.pickItemKey.boundKey.code) return@onPress

			if (!player.hasFireworks) {
				warn("You need to have fireworks in your inventory to use this module!")
				return@onPress
			}
			// Prevent using multiple times
			if (takeoffState != TakeoffState.Idle) return@onPress
			// If already gliding use another firework
			if (player.canStartGliding || player.isGliding) takeoffState = TakeoffState.StartFlying
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

	private var takeoffState = TakeoffState.Idle

	val ClientPlayerEntity.hasFireworks: Boolean
		get() = Items.FIREWORK_ROCKET.select()
			.filter(HotbarAndInventoryContainer.stacks)
			.isNotEmpty() || offHandStack.item == Items.FIREWORK_ROCKET

	context(_: SafeContext)
	private val ClientPlayerEntity.canTakeoff: Boolean
		get() = with(PlayerUtils) { canTakeoff } && hasFireworks

	init {
		setDefaultAutomationConfig()
			.withEdits {
				hideAllExcept(::hotbarConfig, ::inventoryConfig)
				hotbarConfig::tickStageMask.editSetting { defaultValue(mutableSetOf(TickEvent.Pre)) }
				inventoryConfig::tickStageMask.editSetting { defaultValue(mutableSetOf(TickEvent.Pre)) }
			}

		listen<TickEvent.Pre> {
			when (takeoffState) {
				TakeoffState.Idle -> {}
				TakeoffState.Jumping -> {
					player.jump()
					takeoffState = TakeoffState.StartFlying
				}
				TakeoffState.StartFlying -> {
					if (player.canStartGliding) GlideHandler.onGlide()
					startFirework(invUse)
					takeoffState = TakeoffState.Idle
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
					} else if (player.canStartGliding) {
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
		val selection = Items.FIREWORK_ROCKET.select(1)

		val hotbarMatch = selection.bestMatch(HotbarContainer.slots)
		if (hotbarMatch != null) {
			swapAndFirework(hotbarMatch.index)
			return
		}

		if (!inventory) return

		val inventoryMatch = selection.bestMatch(InventoryContainer.slots) ?: return
		val hotbarSlot = selectStack { isEmpty() }.bestMatch(HotbarContainer.slots)?.index ?: 8

		inventoryRequest {
			swapWithHotbar(inventoryMatch.id, hotbarSlot)
			action { swapAndFirework(hotbarSlot) }
			swapWithHotbar(inventoryMatch.id, hotbarSlot)
		}.submit()
	}

	private fun SafeContext.swapAndFirework(hotbarSlot: Int) {
		val request =
			hotbarRequest(hotbarSlot, true) {
				keepTicks(0)
			}.submit(false)
		if (request.done) {
			interaction.interactItem(player, Hand.MAIN_HAND)
			sendSwing()
		}
	}

	enum class TakeoffState {
		Idle,
		Jumping,
		StartFlying
	}
}
