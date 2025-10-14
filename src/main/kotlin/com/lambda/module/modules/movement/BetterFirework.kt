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

import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.screen.PlayerScreenHandler
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.Hand
import net.minecraft.util.hit.HitResult

object BetterFirework : Module(
    name = "BetterFirework",
    description = "Automatic takeoff with fireworks",
    tag = ModuleTag.MOVEMENT,
) {
    private var fireworkInteract by setting("Firework Interact", true, "Automatically start flying when right clicking fireworks")
    private var fireworkInteractCancel by setting("Firework Interact Cancel", false, "Cancel block interactions while holding fireworks", visibility = { fireworkInteract })
    private var middleClick by setting("Middle Click", true, "Use firework on middle mouse click")
    private var middleClickCancel by setting("Middle Click Cancel", false, description = "Cancel pick block action on middle mouse click", visibility = { middleClick })
    private var clientSwing by setting("Swing", true, "Swing hand client side")
    private var silentUse by setting("Silent", true, "Silent use fireworks from the inventory", visibility = { middleClick })

    private var takeoffState = TakeoffState.NONE

    init {
        listen<TickEvent.Pre> {
            when (takeoffState) {
                TakeoffState.NONE -> {
                    // Nothing to do
                }
                TakeoffState.JUMPING -> {
                    player.jump()
                    takeoffState = TakeoffState.START_FLYING
                }
                TakeoffState.START_FLYING -> {
                    if (player.canOpenElytra) {
                        player.startGliding()
                        connection.sendPacket(ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING))
                    }
                    startFirework(silentUse)
                    takeoffState = TakeoffState.NONE
                }
            }
        }
    }

    /**
     * Returns true if the mc item interaction should be canceled
     */
    fun onInteract() =
        runSafe {
            if (!fireworkInteract) return false
            if (player.inventory.selectedStack?.item != Items.FIREWORK_ROCKET) {
                return false
            }
            if (player.isGliding) {
                return false // No need to do special magic if we are already holding fireworks and flying
            }
            if (mc.crosshairTarget != null && mc.crosshairTarget!!.type != HitResult.Type.MISS && !fireworkInteractCancel) {
                return false
            }
            mc.itemUseCooldown += 4
            val cancelInteract = player.canTakeoff || fireworkInteractCancel
            if (player.canTakeoff) {
                takeoffState = TakeoffState.JUMPING
            } else if (player.canOpenElytra) {
                takeoffState = TakeoffState.START_FLYING
            }
            return cancelInteract
        } ?: false

    /**
     * Returns true when the pick interaction should be canceled.
     */
    fun onPick() =
        runSafe {
            if (!middleClick) return false
            if (mc.crosshairTarget?.type == HitResult.Type.BLOCK && !middleClickCancel) {
                return false
            }
            if (takeoffState != TakeoffState.NONE) {
                return false // Prevent using multiple times
            }
            if (player.canOpenElytra || player.isGliding) {
                // If already gliding use another firework
                takeoffState = TakeoffState.START_FLYING
            } else if (player.canTakeoff) {
                takeoffState = TakeoffState.JUMPING
            }
            return true
        } ?: false // Edouardo:        :3333333

    val ClientPlayerEntity.canTakeoff: Boolean
        get() = isOnGround || canOpenElytra

    val ClientPlayerEntity.canOpenElytra: Boolean
        get() = !abilities.flying && !isClimbing && !isGliding && !isTouchingWater && !isOnGround && !hasVehicle() && !hasStatusEffect(StatusEffects.LEVITATION)

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
    fun SafeContext.startFirework(silent: Boolean): Boolean {
        val fireworkSlot = getFireworkAtHotbar()
        if (fireworkSlot != -1) {
            player.networkHandler.sendPacket(UpdateSelectedSlotC2SPacket(fireworkSlot))
            player.networkHandler.sendPacket(PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, player.yaw, player.pitch))
            sendSwing()
            player.networkHandler.sendPacket(UpdateSelectedSlotC2SPacket(player.getInventory().selectedSlot))
            return true
        } else if (silent) {
            val fireworkIndex = getFireworkInInventoryScreen()
            if (fireworkIndex != -1) {
                interaction.clickSlot(player.playerScreenHandler.syncId, fireworkIndex, 0, SlotActionType.SWAP, mc.player)

                if (player.getInventory().selectedSlot != 0) {
                    player.networkHandler.sendPacket(UpdateSelectedSlotC2SPacket(0))
                    player.networkHandler.sendPacket(PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, player.yaw, player.pitch))
                    sendSwing()
                    player.networkHandler.sendPacket(UpdateSelectedSlotC2SPacket(player.getInventory().selectedSlot))
                } else {
                    player.networkHandler.sendPacket(PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, player.getYaw(), player.getPitch()))
                    sendSwing()
                }

                interaction.clickSlot(player.playerScreenHandler.syncId, fireworkIndex, 0, SlotActionType.SWAP, mc.player)
                return true
            }
        }
        return false
    }

    private fun SafeContext.getFireworkAtHotbar(): Int {
        for (i in 0..8) {
            val itemStack: ItemStack = player.getInventory().getStack(i)
            if (itemStack.item !== Items.FIREWORK_ROCKET) continue
            return i
        }
        return -1
    }

    private fun SafeContext.getFireworkInInventoryScreen(): Int {
        val screenHandler: PlayerScreenHandler = player.playerScreenHandler
        for (i in PlayerScreenHandler.INVENTORY_START..<PlayerScreenHandler.INVENTORY_END) {
            val itemStack = screenHandler.getSlot(i).stack
            if (itemStack.item !== Items.FIREWORK_ROCKET) continue
            return i
        }
        return -1
    }

    enum class TakeoffState {
        NONE,
        JUMPING,
        START_FLYING
    }
}