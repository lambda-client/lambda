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

package com.lambda.interaction.material.container.containers

import com.lambda.Lambda.mc
import com.lambda.context.Automated
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.material.ContainerTask
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.interaction.request.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.util.item.ItemStackUtils.equal
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import net.minecraft.item.ItemStack
import net.minecraft.util.Hand

object MainHandContainer : MaterialContainer(Rank.MainHand) {
    override var stacks: List<ItemStack>
        get() = mc.player?.mainHandStack?.let { listOf(it) } ?: emptyList()
        set(_) {}

    override val description = buildText { literal("MainHand") }

    class HandDeposit @Ta5kBuilder constructor(
        val selection: StackSelection,
        val hand: Hand,
        automated: Automated
    ) : ContainerTask(), Automated by automated {
        override val name: String get() = "Depositing [$selection] to ${hand.name.lowercase().replace("_", " ")}"

        init {
            listen<TickEvent.Pre> {
                val moveStack = InventoryContainer.matchingStacks(selection).firstOrNull() ?: run {
                    failure("No matching stacks found in inventory")
                    return@listen
                }

                val handStack = player.getStackInHand(hand)
                if (moveStack.equal(handStack)) {
                    success()
                    return@listen
                }

                inventoryRequest {
                    val stackInOffHand = moveStack.equal(player.offHandStack)
                    val stackInMainHand = moveStack.equal(player.mainHandStack)
                    if ((hand == Hand.MAIN_HAND && stackInOffHand) || (hand == Hand.OFF_HAND && stackInMainHand)) {
                        swapHands()
                        return@inventoryRequest
                    }

                    val slot = player.currentScreenHandler.slots.firstOrNull { it.stack == moveStack }
                        ?: run {
                            failure(IllegalStateException("Cannot find stack in inventory"))
                            return@inventoryRequest
                        }
                    swap(slot.id, player.inventory.selectedSlot)

                    if (hand == Hand.OFF_HAND) swapHands()

                    onComplete { success() }
                }.submit(queueIfClosed = false)
            }
        }
    }

    context(automated: Automated)
    override fun deposit(selection: StackSelection) = HandDeposit(selection, Hand.MAIN_HAND, automated)
}
