package com.lambda.task.tasks

import com.lambda.context.SafeContext
import com.lambda.event.events.ScreenHandlerEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.material.StackSelection
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.Task
import com.lambda.threading.runConcurrent
import com.lambda.threading.runGameScheduled
import com.lambda.util.item.ItemUtils.block
import com.lambda.util.player.SlotUtils.clickSlot
import com.lambda.util.primitives.extension.containerSlots
import com.lambda.util.primitives.extension.inventorySlots
import kotlinx.coroutines.delay
import net.minecraft.item.ItemStack
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
    private val selectedTo = to.filter { !it.hasStack() }

    init {
        // ToDo: Needs smart code to move as efficient as possible.
        //  Also should handle overflow etc. Should be more generic
        listener<TickEvent.Pre> {
            selector.filterSlots(from).firstOrNull { it.hasStack() }?.let { from ->
//                player.currentScreenHandler
//                    .inventorySlots
//                    .firstOrNull {
//                        it.stack.item.block in TaskFlow.disposables || it.stack.isEmpty
//                    }?.let { to ->
//                        clickSlot(from.id, 0, SlotActionType.PICKUP)
//                        clickSlot(to.id, 0, SlotActionType.PICKUP)
//                        // ToDo: Handle overflow of cursor
//                    }

                player.currentScreenHandler
                    .inventorySlots
                    .firstOrNull {
                        it.stack.item.block in TaskFlow.disposables || it.stack.isEmpty
                    }?.let { emptySlot ->
                        clickSlot(emptySlot.id, 0, SlotActionType.SWAP)
                        clickSlot(from.id, 0, SlotActionType.SWAP)
                    }
            } ?: finish()
        }

        listener<TickEvent.Post> {
            val moved = selector.filterSlots(to)
                .filter { it.hasStack() }
                .sumOf { it.stack.count } >= selector.count

            if (selectedFrom.isEmpty() || moved) {
                finish()
            }
        }
    }

    private fun SafeContext.finish() {
        if (closeScreen) player.closeHandledScreen()
        success(Unit)
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
