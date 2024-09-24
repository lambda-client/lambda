package com.lambda.task.tasks

import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.material.StackSelection
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.Task
import com.lambda.util.extension.containerSlots
import com.lambda.util.extension.inventorySlots
import com.lambda.util.item.ItemUtils.block
import com.lambda.util.player.SlotUtils
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType

class InventoryTask(
    val screen: ScreenHandler,
    private val selector: StackSelection,
    val from: List<Slot>,
    val to: List<Slot>,
    private val closeScreen: Boolean = true
) : Task<Unit>() {
    private val selectedFrom get() = selector.filterSlots(from).filter { it.hasStack() }
    private val selectedTo = to.filter { it.stack.isEmpty } + to.filter { it.stack.item.block in TaskFlow.disposables }
    private val transactions = mutableListOf<SlotUtils.Transaction>()

    override fun SafeContext.onStart() {
        selectedFrom.zip(selectedTo).forEach { (from, to) ->
            transactions.add(SlotUtils.Transaction(to.id, 0, SlotActionType.SWAP))
            transactions.add(SlotUtils.Transaction(from.id, 0, SlotActionType.SWAP))

            // ToDo: Handle overflow of cursor for PICKUP
        }
    }

    init {
        // ToDo: Needs smart code to move as efficient as possible.
        //  Also should handle overflow etc. Should be more generic
        listener<TickEvent.Pre> {
            val moved = selector.filterSlots(to)
                .filter { it.hasStack() }
                .sumOf { it.stack.count } >= selector.count

            if (transactions.isEmpty() || moved) {
                if (closeScreen) player.closeHandledScreen()
                success(Unit)
            }

            transactions.removeFirst().click()
        }
    }

    companion object {
        @Ta5kBuilder
        fun moveItems(
            screen: ScreenHandler,
            selection: StackSelection,
            from: List<Slot>,
            to: List<Slot>,
            closeScreen: Boolean = true
        ) = InventoryTask(screen, selection, from, to, closeScreen)

        @Ta5kBuilder
        fun withdraw(screen: ScreenHandler, selection: StackSelection) =
            moveItems(screen, selection, screen.containerSlots, screen.inventorySlots)

        @Ta5kBuilder
        fun deposit(screen: ScreenHandler, selection: StackSelection) =
            moveItems(screen, selection, screen.inventorySlots, screen.containerSlots)
    }
}
