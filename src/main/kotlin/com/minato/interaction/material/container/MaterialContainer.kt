
package com.minato.interaction.material.container

import com.minato.context.Automated
import com.minato.context.AutomatedSafeContext
import com.minato.context.SafeContext
import com.minato.event.EventFlow.post
import com.minato.event.events.ContainerEvent
import com.minato.interaction.managers.inventory.InventoryRequest
import com.minato.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.minato.interaction.material.StackSelection
import com.minato.interaction.material.container.containers.ShulkerBoxContainer
import com.minato.task.Task
import com.minato.task.tasks.ContainerTransferTask
import com.minato.util.Nameable
import com.minato.util.item.ItemStackUtils.count
import com.minato.util.item.ItemStackUtils.empty
import com.minato.util.item.ItemStackUtils.shulkerBoxContents
import com.minato.util.item.ItemStackUtils.spaceLeft
import com.minato.util.item.ItemUtils
import com.minato.util.item.ItemUtils.toItemCount
import com.minato.util.text.TextBuilder
import com.minato.util.text.TextDsl
import com.minato.util.text.buildText
import com.minato.util.text.highlighted
import com.minato.util.text.literal
import com.minato.util.text.text
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
        !it.stack.item.components.contains(DataComponentTypes.CONSUMABLE)
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
            val transferEvent = ContainerEvent.Transfer(fromSlot, toSlot, this@MaterialContainer, destination)
            if (transferEvent.post().isCanceled()) return false
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
