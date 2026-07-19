
package com.minato.module.modules.movement

import com.minato.Minato
import com.minato.Minato.mc
import com.minato.config.ConfigEditor.editSetting
import com.minato.config.ConfigEditor.hideAllExcept
import com.minato.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.minato.config.settings.complex.Bind
import com.minato.config.settings.complex.KeybindSetting.Companion.onPress
import com.minato.config.withEdits
import com.minato.context.SafeContext
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.handlers.GlideHandler
import com.minato.interaction.managers.hotbar.HotbarRequest
import com.minato.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.minato.interaction.material.StackSelection.Companion.select
import com.minato.interaction.material.StackSelection.Companion.selectStack
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.threading.runSafe
import com.minato.util.CommunicationUtils.warn
import com.minato.util.KeyCode
import com.minato.util.Mouse
import com.minato.util.player.PlayerUtils
import com.minato.util.player.PlayerUtils.canStartGliding
import com.minato.util.player.SlotUtils.hotbarAndInventoryStacks
import com.minato.util.player.SlotUtils.hotbarStacks
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
				activateButton.mouse == Minato.mc.options.pickItemKey.boundKey.code) return@onPress

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
			.filterStacks(hotbarAndInventoryStacks)
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
		Idle,
		Jumping,
		StartFlying
	}
}
