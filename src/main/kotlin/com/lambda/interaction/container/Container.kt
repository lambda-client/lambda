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

package com.lambda.interaction.container

import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.post
import com.lambda.event.events.ContainerEvent
import com.lambda.interaction.container.containers.external.ShulkerBoxContainer
import com.lambda.interaction.container.selection.StackSelection
import com.lambda.interaction.manager.managers.inventory.InvRequestBuilder
import com.lambda.interaction.manager.managers.inventory.InvRequestBuilder.Companion.inventoryRequest
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.util.Nameable
import com.lambda.util.item.ItemStackUtils.count
import com.lambda.util.item.ItemStackUtils.emptySpace
import com.lambda.util.item.ItemStackUtils.shulkerBoxStacks
import com.lambda.util.item.ItemStackUtils.spaceLeft
import com.lambda.util.item.ItemUtils.SHULKER_BOXES
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
abstract class Container(
    val type: ContainerType
) : Nameable, Comparable<Container> {
    override val name: String
        get() = description.string

    abstract val slots: List<Slot>
    abstract var stacks: List<ItemStack>
    val storedContainers = mutableMapOf<Int, NestedContainer>()

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
        !it.stack.item.components.contains(DataComponentTypes.CONSUMABLE)
    }.thenByDescending {
        it.stack.isStackable
    }

    open val isAccessed get() = true

    context(_: SafeContext)
    fun descriptionAndStock(selection: StackSelection) =
        buildText {
            text(description)
            stock(selection)
        }

    @TextDsl
    context(_: SafeContext)
    fun TextBuilder.stock(selection: StackSelection) {
        literal("\n")
        literal("Contains ")
        val available = count(selection)
        highlighted(if (available == Int.MAX_VALUE) "∞" else available.toItemCount())
        literal(" of ")
        highlighted("${selection.optimalStack?.name?.string}")
        literal("\n")
        literal("Could store ")
        val left = spaceLeft(selection)
        highlighted(if (left == Int.MAX_VALUE) "∞" else left.toItemCount())
        literal(" of ")
        highlighted("${selection.optimalStack?.name?.string}")
    }

    fun update(slots: List<ItemStack>) {
        this.stacks = slots
    }

    fun scanStacksForNestedContainers() {
        stacks.forEachIndexed { index, stack ->
            if (stack.item in SHULKER_BOXES) {
                storedContainers[index] =
                    ShulkerBoxContainer(
                        stack.name.string,
                        stack.item,
                        stack.shulkerBoxStacks,
                        this,
                        index
                    ).also { it.scanStacksForNestedContainers() }
            } else storedContainers.remove(index)
        }
    }

    @Ta5kBuilder
    context(automated: Automated)
    open fun access(): OpenContainerTask<*>? = null

    open fun count(selection: StackSelection) =
        slots.takeUnless { it.isEmpty() }?.let { selection.filter(it).count }
            ?: selection.filter(stacks).count

    open fun spaceLeft(selection: StackSelection) =
	    slots.takeUnless { it.isEmpty() }?.let { selection.filter(it).spaceLeft + it.emptySpace }
		    ?: (selection.filter(stacks).spaceLeft + stacks.emptySpace)

    open fun findSlots(selection: StackSelection) = selection.filter(slots)

    fun findSlot(selection: StackSelection) = findSlots(selection).firstOrNull()

    open fun findStacks(selection: StackSelection) = selection.filter(stacks)

    fun findStack(selection: StackSelection) = findStacks(selection).firstOrNull()

    context(_: Automated)
    fun findMoveSlots(
        selection: StackSelection,
        toContainer: Container,
        toStackSelection: StackSelection = StackSelection.ANYTHING
    ) = Pair(selection.filter(slots).firstOrNull(), toContainer.findReplaceSlot(toStackSelection))

    context(_: Automated)
    open fun findReplaceSlot(
        selection: StackSelection = StackSelection.ANYTHING
    ) = selection.filter(slots)
        .sortedWith(replaceSorter)
        .firstOrNull()

    context(automatedSafeContext: AutomatedSafeContext)
    internal fun swap(fromSlot: Slot, toSlot: Slot, toContainer: Container): Boolean {
        val transferEvent = ContainerEvent.Transfer(fromSlot, toSlot, this@Container, toContainer)
        if (transferEvent.post().isCanceled()) return false
        return automatedSafeContext.inventoryRequest {
            if (swapMethodPriority > toContainer.swapMethodPriority) swap(fromSlot, toSlot)
            else with(toContainer) { swap(toSlot, fromSlot) }
        }.submit().done
    }

    context(safeContext: SafeContext)
    protected open fun InvRequestBuilder.swap(fromHere: Slot, toSlot: Slot) {
        if (fromHere.stack.isEmpty) moveSlot(toSlot.id, fromHere.id)
        else {
            moveSlot(fromHere.id, toSlot.id)
            if (!toSlot.stack.isEmpty) pickup(fromHere.id)
        }
    }

    override fun compareTo(other: Container) = compareBy<Container> { it.type }.compare(this, other)
}
