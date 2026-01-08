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

package com.lambda.interaction.material.container

import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.interaction.managers.inventory.InventoryRequest
import com.lambda.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.containers.ShulkerBoxContainer
import com.lambda.task.Task
import com.lambda.task.tasks.ContainerTransferTask
import com.lambda.util.Nameable
import com.lambda.util.item.ItemStackUtils.count
import com.lambda.util.item.ItemStackUtils.empty
import com.lambda.util.item.ItemStackUtils.shulkerBoxContents
import com.lambda.util.item.ItemStackUtils.spaceLeft
import com.lambda.util.item.ItemUtils
import com.lambda.util.item.ItemUtils.toItemCount
import com.lambda.util.text.TextBuilder
import com.lambda.util.text.TextDsl
import com.lambda.util.text.buildText
import com.lambda.util.text.highlighted
import com.lambda.util.text.literal
import com.lambda.util.text.text
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot
import net.minecraft.text.Text

// ToDo: Make jsonable to persistently store them
abstract class MaterialContainer(
    val rank: Rank
) : Nameable, Comparable<MaterialContainer> {
    context(_: SafeContext)
    abstract val slots: List<Slot>
    abstract var stacks: List<ItemStack>
    open val swapMethodPriority = 0
    abstract val description: Text

    context(automated: Automated)
    open val replaceSorter get() = compareByDescending<Slot> {
        it.stack.isEmpty
    }.thenByDescending {
        it.stack.item in automated.inventoryConfig.disposables
    }.thenByDescending {
        !it.stack.item.components.contains(DataComponentTypes.TOOL)
    }.thenByDescending {
        !it.stack.item.components.contains(DataComponentTypes.FOOD)
    }.thenByDescending {
        it.stack.isStackable
    }

    @TextDsl
    context(_: SafeContext)
    fun TextBuilder.stock(selection: StackSelection) {
        literal("\n")
        literal("Contains ")
        val available = materialAvailable(selection)
        highlighted(if (available == Int.MAX_VALUE) "∞" else available.toItemCount())
        literal(" of ")
        highlighted("${selection.optimalStack?.name?.string}")
        literal("\n")
        literal("Could store ")
        val left = spaceAvailable(selection)
        highlighted(if (left == Int.MAX_VALUE) "∞" else left.toItemCount())
        literal(" of ")
        highlighted("${selection.optimalStack?.name?.string}")
    }

    context(_: SafeContext)
    fun description(selection: StackSelection) =
        buildText {
            text(description)
            stock(selection)
        }

    override val name: String
        get() = buildText { text(description) }.string

    context(_: SafeContext)
    val shulkerContainer
        get() =
            slots.filter {
                it.stack.item in ItemUtils.shulkerBoxes
            }.map { slot ->
                ShulkerBoxContainer(
                    slot.stack.shulkerBoxContents,
                    containedIn = this@MaterialContainer,
                    shulkerSlot = slot
                )
            }.toSet()

    fun update(slots: List<ItemStack>) {
        this.stacks = slots
    }

    class FailureTask(override val name: String) : Task<Unit>() {
        override fun SafeContext.onStart() {
            failure(name)
        }
    }

    context(_: SafeContext)
    open fun InventoryRequest.InvRequestBuilder.transfer(fromHere: Slot, toSlot: Slot) {
        if (fromHere.stack.isEmpty) pickupAndPlace(toSlot.id, fromHere.id)
        else {
            pickupAndPlace(fromHere.id, toSlot.id)
            if (!toSlot.stack.isEmpty) pickup(fromHere.id)
        }
    }

    context(automatedSafeContext: AutomatedSafeContext)
    fun transfer(stackSelection: StackSelection, destination: MaterialContainer): Boolean =
        with(automatedSafeContext) {
            val fromSlot = getSlot(stackSelection) ?: return false
            val toSlot = destination.getReplaceableSlot() ?: return false
            return inventoryRequest {
                if (swapMethodPriority > destination.swapMethodPriority) transfer(fromSlot, toSlot)
                else with(destination) { transfer(toSlot, fromSlot) }
            }.submit().done
        }

    context(automatedSafeContext: AutomatedSafeContext)
    fun transferByTask(stackSelection: StackSelection, destination: MaterialContainer, failIfNoMaterial: Boolean = false) =
        ContainerTransferTask(this, destination, stackSelection, automatedSafeContext, failIfNoMaterial)

    protected fun InventoryRequest.InvRequestBuilder.pickupAndPlace(fromId: Int, toId: Int) {
        pickup(fromId)
        pickup(toId)
    }

    context(_: SafeContext)
    open fun matchingStacks(selection: StackSelection) =
        selection.filterStacks(stacks)

    context(_: SafeContext)
    open fun matchingSlots(selection: StackSelection) =
        selection.filterSlots(slots)

    context(_: SafeContext)
    open fun materialAvailable(selection: StackSelection) =
        matchingStacks(selection).count

    context(_: SafeContext)
    open fun spaceAvailable(selection: StackSelection) =
        matchingStacks(selection).spaceLeft + stacks.empty * selection.stackSize

    context(_: AutomatedSafeContext)
    open fun getReplaceableSlot() = slots.sortedWith(replaceSorter).firstOrNull()

    context(_: SafeContext)
    open fun getSlot(stackSelection: StackSelection): Slot? =
        stackSelection.filterSlots(slots).firstOrNull()

    enum class Rank {
        MainHand,
        OffHand,
        Hotbar,
        Inventory,
        Creative,
        ShulkerBox,
        EnderChest,
        Chest,
        Stash
    }

    override fun compareTo(other: MaterialContainer) =
        compareBy<MaterialContainer> {
            it.rank
        }.compare(this, other)
}
