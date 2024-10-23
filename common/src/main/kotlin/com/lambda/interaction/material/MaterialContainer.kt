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

package com.lambda.interaction.material

import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.ShulkerBoxContainer
import com.lambda.interaction.material.transfer.TransferResult
import com.lambda.task.Task
import com.lambda.util.Nameable
import com.lambda.util.item.ItemStackUtils.count
import com.lambda.util.item.ItemStackUtils.empty
import com.lambda.util.item.ItemStackUtils.shulkerBoxContents
import com.lambda.util.item.ItemStackUtils.spaceLeft
import com.lambda.util.item.ItemUtils
import net.minecraft.item.ItemStack

// ToDo: Make jsonable to persistently store them
abstract class MaterialContainer(
    private val rank: Rank
) : Nameable, Comparable<MaterialContainer> {
    abstract var stacks: List<ItemStack>

    val shulkerContainer
        get() =
            stacks.filter {
                it.item in ItemUtils.shulkerBoxes
            }.map { stack ->
                ShulkerBoxContainer(
                    stack.shulkerBoxContents,
                    containedIn = this@MaterialContainer,
                    shulkerStack = stack,
                )
            }.toSet()

    fun update(stacks: List<ItemStack>) {
        this.stacks = stacks
    }

    /**
     * Withdraws items from the container to the player's inventory.
     */
    @Task.Ta5kBuilder
    abstract fun withdraw(selection: StackSelection): Task<*>

    /**
     * Deposits items from the player's inventory into the container.
     */
    @Task.Ta5kBuilder
    abstract fun deposit(selection: StackSelection): Task<*>

    open fun matchingStacks(selection: StackSelection) =
        selection.filterStacks(stacks)

    open fun matchingStacks(selection: (ItemStack) -> Boolean) =
        matchingStacks(selection.select())

    open fun available(selection: StackSelection) =
        matchingStacks(selection).count

    open fun spaceLeft(selection: StackSelection) =
        matchingStacks(selection).spaceLeft + stacks.empty * selection.stackSize

    fun transfer(selection: StackSelection, destination: MaterialContainer): TransferResult {
        val amount = available(selection)
        if (amount < selection.count) {
            return TransferResult.MissingItems(selection.count - amount)
        }

//        val space = destination.spaceLeft(selection)
//        if (space == 0) {
//            return TransferResult.NoSpace
//        }
//
//        val transferAmount = minOf(amount, space)
//        selection.selector = { true }
//        selection.count = transferAmount

        return TransferResult.Transfer(selection, from = this, to = destination)
    }

    enum class Rank {
        MAIN_HAND,
        OFF_HAND,
        HOTBAR,
        INVENTORY,
        CREATIVE,
        SHULKER_BOX,
        ENDER_CHEST,
        CHEST,
        STASH
    }

    override fun compareTo(other: MaterialContainer) =
        compareBy<MaterialContainer> {
            it.rank
        }.compare(this, other)
}
