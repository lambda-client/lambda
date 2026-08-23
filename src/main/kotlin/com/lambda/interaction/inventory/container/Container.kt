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

package com.lambda.interaction.inventory.container

import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.post
import com.lambda.event.events.ContainerEvent
import com.lambda.interaction.inventory.StackSelection
import com.lambda.interaction.inventory.container.containers.external.ShulkerBoxContainer
import com.lambda.interaction.manager.managers.inventory.InvRequestBuilder
import com.lambda.interaction.manager.managers.inventory.InvRequestBuilder.Companion.inventoryRequest
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.task.tasks.ContainerTransferTask
import com.lambda.task.wrappers.TaskOrNullSupplier
import com.lambda.util.Nameable
import com.lambda.util.item.ItemStackUtils.count
import com.lambda.util.item.ItemStackUtils.empty
import com.lambda.util.item.ItemStackUtils.shulkerBoxStacks
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
abstract class Container(
    val rank: Rank
) : Nameable, Comparable<Container> {
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
        !it.stack.item.components.contains(DataComponentTypes.CONSUMABLE)
    }.thenByDescending {
        it.stack.isStackable
    }

    @TextDsl
    context(_: SafeContext)
    fun TextBuilder.stock(selection: StackSelection) {
        literal("\n")
        literal("Contains ")
        val available = stackCount(selection)
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

    //ToDo: Bundles
    val shulkerContainers
        get() =
            slots.takeIf {
                it.isEmpty()
            }?.filter {
                it.stack.item in ItemUtils.shulkerBoxes
            }?.map { slot ->
                ShulkerBoxContainer(
                    slot.stack.shulkerBoxStacks,
                    this@Container,
                    slot
                )
            }?.toSet()
                ?: stacks.map { stack ->
                    ShulkerBoxContainer(
                        stack.shulkerBoxStacks,
                        this@Container,
                        null
                    )
                }.toSet()

    open val isAccessed get() = true

    fun update(slots: List<ItemStack>) {
        this.stacks = slots
    }

    @Ta5kBuilder
    context(automatedSafeContext: AutomatedSafeContext)
    open fun <R> accessThen(
        closeAfter: Boolean = true,
        afterOpen: TaskOrNullSupplier<Unit, R?> = { null },
        afterClose: TaskOrNullSupplier<R?, *> = { null }
    ) = with(automatedSafeContext) {
        taskOrSkipOrNull({ afterOpen(Unit) }) { result ->
            if (closeAfter) afterClose(result)
            else null
        }
    }

    context(automatedSafeContext: AutomatedSafeContext)
    fun transfer(selection: StackSelection, toContainer: Container): Boolean =
        with(automatedSafeContext) {
            val (fromSlot, toSlot) = getTransferSlots(selection, toContainer)
            if (fromSlot == null || toSlot == null) return false
            return transfer(fromSlot, toSlot, toContainer)
        }

    context(_: Automated)
    fun getTransferSlots(selection: StackSelection, destination: Container): Pair<Slot?, Slot?> =
        Pair(getSlot(selection), destination.getReplaceSlot())

    context(automatedSafeContext: AutomatedSafeContext)
    fun transfer(fromSlot: Slot, toSlot: Slot, toContainer: Container): Boolean {
        val transferEvent = ContainerEvent.Transfer(fromSlot, toSlot, this@Container, toContainer)
        if (transferEvent.post().isCanceled()) return false
        return automatedSafeContext.inventoryRequest {
            if (swapMethodPriority > toContainer.swapMethodPriority) transfer(fromSlot, toSlot)
            else with(toContainer) { transfer(toSlot, fromSlot) }
        }.submit().done
    }

    context(safeContext: SafeContext)
    open fun InvRequestBuilder.transfer(fromHere: Slot, toSlot: Slot) {
        if (fromHere.stack.isEmpty) moveSlot(toSlot.id, fromHere.id)
        else {
            moveSlot(fromHere.id, toSlot.id)
            if (!toSlot.stack.isEmpty) pickup(fromHere.id)
        }
    }

    context(automated: Automated)
    fun transferByTask(stackSelection: StackSelection, toContainer: Container, failIfNoStack: Boolean = true) =
        ContainerTransferTask(this, toContainer, stackSelection, failIfNoStack, automated)

    open fun stackCount(selection: StackSelection) =
        selection.filter(stacks).count

    open fun spaceAvailable(selection: StackSelection) =
        selection.filter(stacks).spaceLeft + stacks.empty

    context(_: Automated)
    open fun getReplaceSlot() = slots.sortedWith(replaceSorter).firstOrNull()

    open fun getSlot(selection: StackSelection) = selection.bestMatch(slots)

    enum class Rank {
        MainHand,
        OffHand,
        Hotbar,
        Inventory,
        HotbarAndInventory,
        Armor,
        Player,
        Creative,
        ShulkerBox,
        EnderChest,
        PlacedShulkerBox,
        PlacedEnderChest,
        Chest,
        Stash
    }

    override fun compareTo(other: Container) = compareBy<Container> { it.rank }.compare(this, other)
}
