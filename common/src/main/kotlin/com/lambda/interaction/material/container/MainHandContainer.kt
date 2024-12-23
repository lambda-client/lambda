/*
 * Copyright 2024 Lambda
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

package com.lambda.interaction.material.container

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.interaction.material.ContainerTask
import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.util.player.SlotUtils.combined
import com.lambda.util.player.SlotUtils.hotbar
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object MainHandContainer : MaterialContainer(Rank.MAIN_HAND) {
    override var stacks: List<ItemStack>
        get() = mc.player?.mainHandStack?.let { listOf(it) } ?: emptyList()
        set(_) {}
    override val name = "MainHand"

    class MainHandDeposit @Ta5kBuilder constructor(val selection: StackSelection, val hand: Hand) : ContainerTask() {
        override val name: String get() = "Depositing $selection to main hand"

        override fun SafeContext.onStart() {
            val moveStack = InventoryContainer.matchingStacks(selection).firstOrNull() ?: return

            val otherHand = if (hand == Hand.MAIN_HAND) Hand.OFF_HAND else Hand.MAIN_HAND
            val handStack = player.getStackInHand(hand)
            val otherStack = player.getStackInHand(otherHand)
            if (ItemStack.areEqual(moveStack, handStack)) {
                delayedFinish()
                return
            }

            if (ItemStack.areEqual(moveStack, otherStack)) {
                connection.sendPacket(
                    PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                        BlockPos.ORIGIN,
                        Direction.DOWN,
                    ),
                )
                delayedFinish()
                return
            }

            if (moveStack in player.hotbar) {
                player.inventory.selectedSlot = player.hotbar.indexOf(moveStack)
                delayedFinish()
                return
            }

            interaction.pickFromInventory(player.combined.indexOf(moveStack))
            delayedFinish()
        }
    }

    override fun deposit(selection: StackSelection) = MainHandDeposit(selection, Hand.MAIN_HAND)
}
