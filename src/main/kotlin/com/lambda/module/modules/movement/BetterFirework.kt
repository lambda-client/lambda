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
    description = "Improves firework usage",
    tag = ModuleTag.MOVEMENT,
) {
    private var autoTakeoff by setting("Auto Takeoff", true)
    private var silent by setting("Silent", true)
    private var swing by setting("Swing ", true)
    private var middleClick by setting("Middle click", true)
    private var middleClickCancel by setting("Middle Click Cancel", false, visibility = { middleClick })

    private var state = BetterFireworkState.IDLE
    private var openDelay = 0

    init {
        listen<TickEvent.Pre> {
            if (openDelay > 0) {
                openDelay--
                if (openDelay == 0 && state == BetterFireworkState.WAIT_AIRBORNE) {
                    state = BetterFireworkState.IDLE
                    startFlying()
                }
            }
        }
    }

    fun onInteract(): Boolean {
        if (!autoTakeoff) return false

        runSafe {
            if (player.inventory.selectedStack?.item == Items.FIREWORK_ROCKET) {
                if (mc.crosshairTarget != null && mc.crosshairTarget!!.type != HitResult.Type.MISS) {
                    return false
                }

                mc.itemUseCooldown += 4
                return startFlying()
            }
        }
        return false
    }

    // Do on pick so mc does not select blocks on middle click after we start flying
    fun onPick() =
        runSafe {
            if (!middleClick) return false
            if (mc.crosshairTarget?.type == HitResult.Type.BLOCK && !middleClickCancel) {
                return false
            }
            if (player.isGliding) {
                startFirework(silent)
            } else {
                startFlying()
            }
            return true
        } ?: false // Edouardo:        :3333333

    fun SafeContext.startFlying(): Boolean {
        if (player.isOnGround) {
            state = BetterFireworkState.WAIT_AIRBORNE
            player.jump()
            openDelay = 1; // Magic number
            return true
        }

        if (player.isGliding) return false

        if (canOpenElytra(player)) {
            connection.sendPacket(ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING))
            startFirework(silent)
            return true
        }

        return false
    }

    fun canOpenElytra(player: ClientPlayerEntity): Boolean {
        return !player.abilities.flying && !player.hasVehicle() && !player.isClimbing && player.checkGliding()
    }

    fun SafeContext.sendSwing() {
        if (swing) {
            player.swingHand(Hand.MAIN_HAND)
        } else {
            connection.sendPacket(HandSwingC2SPacket(Hand.MAIN_HAND))
        }
    }

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

    enum class BetterFireworkState {
        IDLE,
        WAIT_AIRBORNE
    }
}