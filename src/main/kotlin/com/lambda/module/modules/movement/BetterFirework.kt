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

import com.lambda.config.groups.HotbarSettings
import com.lambda.config.settings.collections.SetSetting.Companion.immutableSet
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.interaction.request.hotbar.HotbarManager
import com.lambda.interaction.request.hotbar.HotbarRequest
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.NamedEnum
import com.lambda.util.player.SlotUtils.hotbar
import com.lambda.util.player.SlotUtils.hotbarAndStorage
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.Hand
import net.minecraft.util.hit.HitResult

object BetterFirework : Module(
    name = "BetterFirework",
    description = "Automatic takeoff with fireworks",
    tag = ModuleTag.MOVEMENT,
) {
    private var middleClick by setting("Middle Click", true, "Use firework on middle mouse click").group(Group.General)
    private var middleClickCancel by setting("Middle Click Cancel", false, description = "Cancel pick block action on middle mouse click") { middleClick }.group(Group.General)
    private var fireworkInteract by setting("Right Click Fly", true, "Automatically start flying when right clicking fireworks")
    private var fireworkInteractCancel by setting("Right Click Cancel", false, "Cancel block interactions while holding fireworks") { fireworkInteract }

    private var clientSwing by setting("Swing", true, "Swing hand client side").group(Group.General)
    private var silentUse by setting("Silent", true, "Silent use fireworks from the inventory") { middleClick }.group(Group.General)

    override val hotbarConfig = HotbarSettings(this, Group.Hotbar).apply {
        ::sequenceStageMask.edit { immutableSet(setOf(TickEvent.Pre)) }
    }

    private enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        Hotbar("Hotbar")
    }

    private var takeoffState = TakeoffState.None

    val ClientPlayerEntity.canTakeoff: Boolean
        get() = isOnGround || canOpenElytra

    val ClientPlayerEntity.canOpenElytra: Boolean
        get() = !abilities.flying && !isClimbing && !isGliding && !isTouchingWater && !isOnGround && !hasVehicle() && !hasStatusEffect(StatusEffects.LEVITATION)

    init {
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
                    startFirework(silentUse)
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
                takeoffState = TakeoffState.Jumping
            } else if (player.canOpenElytra) {
                takeoffState = TakeoffState.StartFlying
            }
            return cancelInteract
        } ?: false

    /**
     * Returns true when the pick interaction should be canceled.
     */
    @JvmStatic
    fun onPick() =
        runSafe {
            if (!middleClick) return false
            if (mc.crosshairTarget?.type == HitResult.Type.BLOCK && !middleClickCancel) {
                return false
            }
            if (takeoffState != TakeoffState.None) {
                return false // Prevent using multiple times
            }
            if (player.canOpenElytra || player.isGliding) {
                // If already gliding use another firework
                takeoffState = TakeoffState.StartFlying
            } else if (player.canTakeoff) {
                takeoffState = TakeoffState.Jumping
            }
            return true
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
    fun SafeContext.startFirework(silent: Boolean): Boolean {
        val stack = selectStack(count = 1) { isItem(Items.FIREWORK_ROCKET) }

        stack.bestItemMatch(player.hotbar)
            ?.let {
                val request = HotbarManager.request(HotbarRequest(player.hotbar.indexOf(it), this@BetterFirework, keepTicks = 0))
                if (request.done) {
                    player.networkHandler.sendPacket(PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, player.yaw, player.pitch))
                    sendSwing()
                }

                return true
            }

        if (!silent) return false

        stack.bestItemMatch(player.hotbarAndStorage)
            ?.let {
                val swap = player.hotbarAndStorage.indexOf(it)

                interaction.clickSlot(player.playerScreenHandler.syncId, swap, 0, SlotActionType.SWAP, mc.player)

                val request = HotbarManager.request(HotbarRequest(0, this@BetterFirework, keepTicks = 0))
                if (request.done) {
                    player.networkHandler.sendPacket(PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, player.yaw, player.pitch))
                    sendSwing()
                }

                interaction.clickSlot(player.playerScreenHandler.syncId, swap, 0, SlotActionType.SWAP, mc.player)
                return true
            }

        return false
    }

    enum class TakeoffState {
        None,
        Jumping,
        StartFlying
    }
}
