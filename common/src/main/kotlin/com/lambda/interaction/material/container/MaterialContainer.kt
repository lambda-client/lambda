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

import com.lambda.context.SafeContext
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.containers.ShulkerBoxContainer
import com.lambda.interaction.material.transfer.TransferResult
import com.lambda.task.Task
import com.lambda.util.Nameable
import com.lambda.util.item.ItemStackUtils.count
import com.lambda.util.item.ItemStackUtils.empty
import com.lambda.util.item.ItemStackUtils.shulkerBoxContents
import com.lambda.util.item.ItemStackUtils.spaceLeft
import com.lambda.util.item.ItemUtils
import com.lambda.util.text.*
import net.minecraft.item.ItemStack
import net.minecraft.text.Text

// ToDo: Make jsonable to persistently store them
abstract class MaterialContainer(
    val rank: Rank,
) : Nameable, Comparable<MaterialContainer> {
    abstract var stacks: List<ItemStack>
    abstract val description: Text

    @TextDsl
    fun TextBuilder.stock(selection: StackSelection) {
        literal("\n")
        literal("Contains ")
        val available = materialAvailable(selection)
        highlighted(if (available == Int.MAX_VALUE) "∞" else available.toString())
        literal(" of ")
        highlighted("${selection.optimalStack?.name?.string}")
        literal("\n")
        literal("Could store ")
        val left = spaceAvailable(selection)
        highlighted(if (left == Int.MAX_VALUE) "∞" else left.toString())
        literal(" of ")
        highlighted("${selection.optimalStack?.name?.string}")
    }

    fun description(selection: StackSelection) =
        buildText {
            text(description)
            stock(selection)
        }

    override val name: String
        get() = buildText { text(description) }.string

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

    class Nothing(override val name: String = "Nothing") : Task<Unit>() {
        override fun SafeContext.onStart() {
            failure(name)
        }
    }

    /**
     * Withdraws items from the container to the player's inventory.
     */
    @Task.Ta5kBuilder
    open fun withdraw(selection: StackSelection): Task<*>? = null

    /**
     * Deposits items from the player's inventory into the container.
     */
    @Task.Ta5kBuilder
    open fun deposit(selection: StackSelection): Task<*>? = null

    open fun matchingStacks(selection: StackSelection) =
        selection.filterStacks(stacks)

    open fun materialAvailable(selection: StackSelection) =
        matchingStacks(selection).count

    open fun spaceAvailable(selection: StackSelection) =
        matchingStacks(selection).spaceLeft + stacks.empty * selection.stackSize

    fun transfer(selection: StackSelection, destination: MaterialContainer): TransferResult {
        val amount = materialAvailable(selection)
        if (amount < selection.count) {
            return TransferResult.MissingItems(selection.count - amount)
        }

//        val space = destination.spaceAvailable(selection)
//        if (space == 0) {
//            return TransferResult.NoSpace
//        }

//        val transferAmount = minOf(amount, space)
//        selection.selector = { true }
//        selection.count = transferAmount

        return TransferResult.ContainerTransfer(selection, from = this, to = destination)
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
