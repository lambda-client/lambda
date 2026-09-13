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

import com.lambda.config.automation.setDefaultAutomationConfig
import com.lambda.config.blocks.EatConfig
import com.lambda.config.blocks.EatConfig.Companion.reasonEating
import com.lambda.config.hideAllExcept
import com.lambda.config.withEdits
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.container.containers.HotbarContainer
import com.lambda.interaction.container.selection.select
import com.lambda.interaction.manager.managers.hotbar.HotbarRequestBuilder.Companion.hotbarRequest
import com.lambda.interaction.manager.managers.inventory.InvRequestBuilder.Companion.inventoryRequest
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import com.lambda.module.modules.combat.AutoTotem
import com.lambda.module.modules.combat.KillAura
import com.lambda.threading.runSafe
import com.lambda.threading.runSafeAutomated
import com.lambda.util.Describable
import com.lambda.util.NamedEnum
import com.lambda.util.extension.fullHealth
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand

@Suppress("unused")
object SilentEat : Module(
    name = "SilentEat",
    description = "Silently eats food from inventory without equipping it",
    tag = ModuleTag.PLAYER,
    modulePriority = 95
) {
    val mode by setting("Mode", Mode.Smart, "Eating mode: Smart (Offhand with AutoTotem safety), Offhand, or MainHand")
    val safetyHealth by setting("Safety Health", 12, 6..36, 1, "Minimum health threshold to allow offhand eating in Smart mode")
    val pauseKillAura by setting("Pause KillAura", true, "Pauses KillAura during MainHand eating to prevent hit cancellation")
    val silentVisual by setting("Silent Visual", true, "Hides the food item visually in first person")

    enum class Mode(override val displayName: String, override val description: String) : NamedEnum, Describable {
        Smart("Smart", "Uses Offhand when safe for simultaneous KillAura/PacketMine, yields to AutoTotem when in danger"),
        Offhand("Offhand", "Swaps food into offhand and restores original item afterwards"),
        MainHand("MainHand", "Uses hotbar silent swap, keeping offhand completely untouched for AutoTotem")
    }

    private enum class State {
        IDLE,
        SWAPPING,
        EATING,
        RESTORING
    }

    private var state = State.IDLE
    private var activeHand: Hand? = null
    private var swappedOffhand = false
    private var foodSlotId: Int = -1
    private var originalOffhandStack: ItemStack = ItemStack.EMPTY
    private var swappedHotbar = false
    private var originalHotbarSlot: Int = -1
    private var originalHotbarItemSlotId: Int = -1
    private var eatStack: ItemStack? = null
    private var reason = EatConfig.Reason.None
    private var holdingUse = false
    private var ticksInState = 0

    @JvmStatic val isSilentEating: Boolean get() = isEnabled && state == State.EATING
    @JvmStatic val isUsingOffhand: Boolean get() = isEnabled && state == State.EATING && activeHand == Hand.OFF_HAND
    @JvmStatic val visualOffhandStack: ItemStack get() = originalOffhandStack

    init {
        setDefaultAutomationConfig()
            .withEdits {
                hideAllExcept(::eatConfig, ::inventoryConfig, ::hotbarConfig)
            }

        AutoTotem.offhandOverride = { isUsingOffhand }

        listen<TickEvent.Input.Post> {
            when (state) {
                State.IDLE -> {
                    val currentReason = runSafeAutomated { reasonEating() }
                    if (!currentReason.shouldEat()) return@listen
                    startEatingProcess(currentReason)
                }
                State.SWAPPING -> tickSwapping()
                State.EATING -> tickEating()
                State.RESTORING -> tickRestoring()
            }
        }

        onDisable {
            runSafe { cleanupAndReset() }
        }
    }

    private fun SafeContext.startEatingProcess(currentReason: EatConfig.Reason) {
        if (player.currentScreenHandler !== player.playerScreenHandler) return

        val targetHand = when (mode) {
            Mode.Smart -> {
                val inDanger = player.fullHealth < safetyHealth || (AutoTotem.isEnabled && AutoTotem.isDangerous(this))
                if (inDanger) Hand.MAIN_HAND else Hand.OFF_HAND
            }
            Mode.Offhand -> Hand.OFF_HAND
            Mode.MainHand -> Hand.MAIN_HAND
        }

        val selection = currentReason.selector()
        val slots = player.currentScreenHandler.slots

        if (targetHand == Hand.OFF_HAND) {
            val alreadyInOffhand = selection.matches(player.offHandStack) && !player.offHandStack.isEmpty
            if (alreadyInOffhand) {
                originalOffhandStack = player.offHandStack.copy()
                foodSlotId = -1
                swappedOffhand = false
                activeHand = Hand.OFF_HAND
                reason = currentReason
                eatStack = player.offHandStack.copy()
                state = State.EATING
                ticksInState = 0
                interactFood(Hand.OFF_HAND)
            } else {
                val foodSlot = slots.firstOrNull { slot ->
                    slot.id in 9..44 && selection.matches(slot.stack) && !slot.stack.isEmpty
                } ?: return

                originalOffhandStack = player.offHandStack.copy()
                foodSlotId = foodSlot.id
                swappedOffhand = true
                activeHand = Hand.OFF_HAND
                reason = currentReason
                eatStack = foodSlot.stack.copy()
                state = State.SWAPPING
                ticksInState = 0

                inventoryRequest {
                    swapWithHotbar(foodSlot.id, 40)
                }.submit()
            }
        } else {
            // Main Hand mode
            val hotbarMatch = HotbarContainer.slots.firstOrNull { selection.matches(it.stack) && !it.stack.isEmpty }
            if (hotbarMatch != null) {
                originalHotbarSlot = player.inventory.selectedSlot
                swappedHotbar = false
                activeHand = Hand.MAIN_HAND
                reason = currentReason
                eatStack = hotbarMatch.stack.copy()

                if (pauseKillAura) KillAura.isPaused = true

                if (hotbarMatch.index == player.inventory.selectedSlot) {
                    state = State.EATING
                    ticksInState = 0
                    interactFood(Hand.MAIN_HAND)
                } else {
                    state = State.SWAPPING
                    ticksInState = 0
                    hotbarRequest(hotbarMatch.index).submit()
                }
            } else {
                val invMatch = slots.firstOrNull { slot ->
                    slot.id in 9..35 && selection.matches(slot.stack) && !slot.stack.isEmpty
                } ?: return

                originalHotbarSlot = player.inventory.selectedSlot
                originalHotbarItemSlotId = invMatch.id
                swappedHotbar = true
                activeHand = Hand.MAIN_HAND
                reason = currentReason
                eatStack = invMatch.stack.copy()

                if (pauseKillAura) KillAura.isPaused = true

                state = State.SWAPPING
                ticksInState = 0

                inventoryRequest {
                    swapWithHotbar(invMatch.id, player.inventory.selectedSlot)
                }.submit()
            }
        }
    }

    private fun SafeContext.tickSwapping() {
        ticksInState++

        val hand = activeHand ?: run {
            cleanupAndReset()
            return
        }

        val currentHeld = if (hand == Hand.OFF_HAND) player.offHandStack else player.mainHandStack
        val selection = reason.selector()

        if (selection.matches(currentHeld) && !currentHeld.isEmpty) {
            // Food is now in hand; start eating!
            eatStack = currentHeld.copy()
            state = State.EATING
            ticksInState = 0
            interactFood(hand)
            return
        }

        // Swap timeout: if not in hand after 10 ticks, abort
        if (ticksInState > 10) {
            cleanupAndReset()
        }
    }

    private fun SafeContext.interactFood(hand: Hand) {
        val result = interaction.interactItem(player, hand)
        if (result is ActionResult.Success) {
            if (result.swingSource == ActionResult.SwingSource.CLIENT) {
                player.swingHand(hand)
            }
            mc.gameRenderer.firstPersonRenderer.resetEquipProgress(hand)
        }
        mc.options.useKey.isPressed = true
        holdingUse = true
    }

    private fun SafeContext.tickEating() {
        ticksInState++

        val hand = activeHand ?: run {
            cleanupAndReset()
            return
        }

        // Safety check for AutoTotem in offhand mode
        if (hand == Hand.OFF_HAND) {
            val inDanger = player.fullHealth < safetyHealth || (AutoTotem.isEnabled && AutoTotem.isDangerous(this))
            if (inDanger) {
                cleanupAndReset()
                return
            }
        }

        // Maintain use key
        if (!holdingUse || !mc.options.useKey.isPressed) {
            mc.options.useKey.isPressed = true
            holdingUse = true
        }

        // If after 3 ticks the player isn't using item, retry interaction once
        if (ticksInState == 3 && !player.isUsingItem) {
            interactFood(hand)
        }

        // Check if food was consumed or should stop
        val currentHeld = if (hand == Hand.OFF_HAND) player.offHandStack else player.mainHandStack
        val isFoodStillHeld = reason.selector().matches(currentHeld) && !currentHeld.isEmpty

        val finishedEating = (ticksInState >= 32 && !player.isUsingItem)
            || (ticksInState >= 32 && !isFoodStillHeld)
            || (!reason.shouldKeepEating(eatStack) && ticksInState >= 5)

        if (finishedEating || ticksInState > 45) {
            state = State.RESTORING
            ticksInState = 0
        }
    }

    private fun SafeContext.tickRestoring() {
        cleanupAndReset()
    }

    private fun SafeContext.cleanupAndReset() {
        if (holdingUse) {
            mc.options.useKey.isPressed = false
            holdingUse = false
        }
        interaction.stopUsingItem(player)

        if (pauseKillAura) {
            KillAura.isPaused = false
        }

        if (swappedOffhand) {
            // Guard: Do not overwrite Totem of Undying if AutoTotem equipped one during an emergency abort
            if (foodSlotId != -1 && player.offHandStack.item != Items.TOTEM_OF_UNDYING) {
                inventoryRequest {
                    swapWithHotbar(foodSlotId, 40)
                }.submit()
            }
            swappedOffhand = false
            foodSlotId = -1
            originalOffhandStack = ItemStack.EMPTY
        }

        if (swappedHotbar) {
            if (originalHotbarItemSlotId != -1 && originalHotbarSlot != -1) {
                inventoryRequest {
                    swapWithHotbar(originalHotbarItemSlotId, originalHotbarSlot)
                }.submit()
            }
            swappedHotbar = false
            originalHotbarItemSlotId = -1
        }

        if (originalHotbarSlot != -1) {
            hotbarRequest(originalHotbarSlot).submit()
            originalHotbarSlot = -1
        }

        state = State.IDLE
        activeHand = null
        eatStack = null
        ticksInState = 0
    }
}
