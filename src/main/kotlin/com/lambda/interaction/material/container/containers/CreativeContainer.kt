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
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.interaction.request.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.task.Task
import com.lambda.util.item.ItemStackUtils.equal
import com.lambda.util.player.gamemode
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import net.minecraft.item.ItemStack

data object CreativeContainer : MaterialContainer(Rank.Creative) {
    override var stacks = emptyList<ItemStack>()

    override val description = buildText { literal("Creative") }

    override fun materialAvailable(selection: StackSelection): Int =
        if (mc.player?.isCreative == true && selection.optimalStack != null) Int.MAX_VALUE else 0

    override fun spaceAvailable(selection: StackSelection): Int =
        if (mc.player?.isCreative == true && selection.optimalStack != null) Int.MAX_VALUE else 0

    class CreativeDeposit @Ta5kBuilder constructor(val selection: StackSelection, automated: Automated) : Task<Unit>(), Automated by automated {
        override val name: String get() = "Removing $selection from creative inventory"

        init {
            listen<TickEvent.Pre> {
                if (!gamemode.isCreative) {
                    // ToDo: Maybe switch gamemode?
                    throw NotInCreativeModeException()
                }

                inventoryRequest {
                    player.currentScreenHandler?.slots?.let { slots ->
                        selection.filterSlots(slots).forEach {
                            clickCreativeStack(ItemStack.EMPTY, it.id)
                        }
                    }
                    onComplete { success() }
                }.submit(queueIfClosed = false)
            }
        }
    }

    context(automated: Automated)
    override fun deposit(selection: StackSelection) = CreativeDeposit(selection, automated)

    class CreativeWithdrawal @Ta5kBuilder constructor(val selection: StackSelection, automated: Automated) : Task<Unit>(), Automated by automated {
        override val name: String get() = "Withdrawing $selection from creative inventory"

        init {
            listen<TickEvent.Pre> {
                selection.optimalStack?.let { optimalStack ->
                    if (player.mainHandStack.equal(optimalStack)) {
                        success()
                        return@listen
                    }

                    if (!gamemode.isCreative) {
                        // ToDo: Maybe switch gamemode?
                        throw NotInCreativeModeException()
                    }

                    inventoryRequest {
                        clickCreativeStack(optimalStack, 36 + player.inventory.selectedSlot)
                        action { player.inventory.selectedStack = optimalStack }
                        onComplete { success() }
                    }.submit(queueIfClosed = false)
                    return@listen
                }

                throw NoOptimalStackException()
            }
        }
    }

    // Withdraws items from the creative menu to the player's main hand
    context(automated: Automated)
    override fun withdraw(selection: StackSelection) = CreativeWithdrawal(selection, automated)

    class NotInCreativeModeException : IllegalStateException("Insufficient permission: not in creative mode")
    class NoOptimalStackException : IllegalStateException("Cannot move item: no optimal stack")
}
