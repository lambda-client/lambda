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
import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task.Companion.buildTask
import com.lambda.task.Task.Companion.emptyTask
import com.lambda.util.player.SlotUtils.combined
import com.lambda.util.player.SlotUtils.hotbar
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object MainHandContainer : MaterialContainer(Rank.MAIN_HAND) {
    override var stacks: List<ItemStack>
        get() = mc.player?.mainHandStack?.let { listOf(it) } ?: emptyList()
        set(_) {}
    override val name = "MainHand"

    override fun withdraw(selection: StackSelection) = emptyTask("WithdrawFromMainHand")

    override fun deposit(selection: StackSelection) = buildTask("DepositToMainHand") {
        InventoryContainer.matchingStacks(selection).firstOrNull()?.let { stack ->
            if (ItemStack.areEqual(stack, player.mainHandStack)) {
                return@buildTask
            }

            if (ItemStack.areEqual(stack, player.offHandStack)) {
                connection.sendPacket(
                    PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                        BlockPos.ORIGIN,
                        Direction.DOWN,
                    ),
                )
                return@buildTask
            }

            if (stack in player.hotbar) {
                player.inventory.selectedSlot = player.hotbar.indexOf(stack)
                return@buildTask
            }

            interaction.pickFromInventory(player.combined.indexOf(stack))
        }
    }
}
