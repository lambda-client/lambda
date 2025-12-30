/*
 * Copyright 2025 Lambda
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

import com.lambda.config.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.applyEdits
import com.lambda.config.settings.complex.Bind
import com.lambda.context.SafeContext
import com.lambda.event.events.KeyboardEvent
import com.lambda.event.events.MouseEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.hotbar.HotbarRequest
import com.lambda.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.player.SlotUtils.hotbar
import com.lambda.util.player.SlotUtils.hotbarAndStorage
import net.minecraft.client.network.AbstractClientPlayerEntity
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult

object BetterFirework : Module(
    name = "BetterFirework",
    description = "Automatic takeoff with fireworks",
    tag = ModuleTag.MOVEMENT,
) {
    private var activateButton by setting("Activate Key", Bind(0, 0, Mouse.Middle.ordinal), "Button to activate Firework")
    private var midFlightActivationKey by setting("Mid-Flight Activation Key", Bind(0, 0), "Firework use key for mid flight activation")
    private var middleClickCancel by setting("Middle Click Cancel", false, description = "Cancel pick block action on middle mouse click") { activateButton.key != KeyCode.Unbound.code }
    private var fireworkInteract by setting("Right Click Fly", true, "Automatically start flying when right clicking fireworks")
    private var fireworkInteractCancel by setting("Right Click Cancel", false, "Cancel block interactions while holding fireworks") { fireworkInteract }

    private var clientSwing by setting("Swing", true, "Swing hand client side")
    private var invUse by setting("Inventory", true, "Use fireworks from inventory") { activateButton.key != KeyCode.Unbound.code }

    private var takeoffState = TakeoffState.None

    val ClientPlayerEntity.canTakeoff: Boolean
        get() = isOnGround || canOpenElytra

    val ClientPlayerEntity.canOpenElytra: Boolean
        get() = !abilities.flying && !isClimbing && !isGliding && !isTouchingWater && !isOnGround && !hasVehicle() && !hasStatusEffect(StatusEffects.LEVITATION)

    init {
		setDefaultAutomationConfig {
			applyEdits {
				hideAllGroupsExcept(hotbarConfig, inventoryConfig)
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
        listen<MouseEvent.Click> {
            if (!it.isPressed) {
                return@listen
            }
            if (it.satisfies(activateButton)) {
                if (activateButton.mouse == mc.options.pickItemKey.boundKey.code) {
                    return@listen
                }
                runSafe {
                    if (takeoffState != TakeoffState.None) {
                        return@listen // Prevent using multiple times
                    }
                    if (player.canOpenElytra || player.isGliding) {
                        // If already gliding use another firework
                        takeoffState = TakeoffState.StartFlying
                    } else if (player.canTakeoff) {
                        takeoffState = TakeoffState.Jumping
                    }
                }
            }
            if (it.satisfies(midFlightActivationKey)) {
                runSafe {
                    if (player.isGliding)
                        takeoffState = TakeoffState.StartFlying
                }
            }
        }
        listen<KeyboardEvent.Press> {
            if (!it.isPressed) {
                return@listen
            }
            if (it.satisfies(activateButton)) {
                if (activateButton.key != mc.options.pickItemKey.boundKey.code) {
                    runSafe {
                        if (takeoffState == TakeoffState.None) {
                            if (player.canOpenElytra || player.isGliding) {
                                // If already gliding use another firework
                                takeoffState = TakeoffState.StartFlying
                            } else if (player.canTakeoff) {
                                takeoffState = TakeoffState.Jumping
                            }
                        }
                    }
                }
            }
            if (it.satisfies(midFlightActivationKey)) {
                runSafe {
                    if (player.isGliding)
                        takeoffState = TakeoffState.StartFlying
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
                        (mc.crosshairTarget != null && mc.crosshairTarget!!.type != HitResult.Type.MISS && !fireworkInteractCancel) -> false
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
     * Returns true when the pick interaction should be canceled.
     */
    @JvmStatic
    fun onPick() =
        runSafe {
            when {
                (mc.crosshairTarget?.type == HitResult.Type.BLOCK && !middleClickCancel) ||
                        (!activateButton.isMouseBind || activateButton.mouse != mc.options.pickItemKey.boundKey.code) ||
                        takeoffState != TakeoffState.None ||
		                (mc.crosshairTarget is EntityHitResult && (mc.crosshairTarget as EntityHitResult).entity is AbstractClientPlayerEntity) -> false // Prevent using multiple times
                else -> {
                    if (player.canOpenElytra || player.isGliding) {
                        // If already gliding use another firework
                        takeoffState = TakeoffState.StartFlying
                    } else if (player.canTakeoff) {
                        takeoffState = TakeoffState.Jumping
                    }
                    middleClickCancel
                }
            }
        } ?: false

    fun SafeContext.sendSwing() {
        if (clientSwing) {
            player.swingHand(Hand.MAIN_HAND)
        } else {
            connection.sendPacket(HandSwingC2SPacket(Hand.MAIN_HAND))
        }
    }

    /**
     * Use a firework from the hotbar or inventory if possible.
     * Return true if a firework has been used
     */
    @JvmStatic
    fun SafeContext.startFirework(silent: Boolean) {
        val stack = selectStack(count = 1) { isItem(Items.FIREWORK_ROCKET) }

        stack.bestItemMatch(player.hotbar)
            ?.let {
                val request = HotbarRequest(player.hotbar.indexOf(it), this@BetterFirework, keepTicks = 0)
                    .submit(queueIfMismatchedStage = false)
                if (request.done) {
                    interaction.interactItem(player, Hand.MAIN_HAND)
                    sendSwing()
                }
                return
            }

        if (!silent) return

        stack.bestItemMatch(player.hotbarAndStorage)
            ?.let {
                val swapSlotId = player.hotbarAndStorage.indexOf(it)
                val hotbarSlotToSwapWith = player.hotbar.find { slot -> slot.isEmpty }?.let { slot -> player.hotbar.indexOf(slot) } ?: 8

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
