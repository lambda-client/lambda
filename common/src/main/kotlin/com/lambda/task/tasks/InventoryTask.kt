package com.lambda.task.tasks

import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.material.StackSelection
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.Task
import com.lambda.util.item.ItemUtils.block
import com.lambda.util.item.ItemUtils.defaultDisposables
import com.lambda.util.player.SlotUtils.clickSlot
import com.lambda.util.extension.containerSlots
import com.lambda.util.extension.inventorySlots
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType

class InventoryTask<H : ScreenHandler>(
    val screen: H,
    val selection: StackSelection,
    val from: List<Slot>,
    val to: List<Slot>,
    private val closeScreen: Boolean = true
) : Task<List<ItemStack>>() {
    private val moved = mutableListOf<ItemStack>()
    private val selectedFrom = selection.filterSlots(from).filter { it.hasStack() }
    private val selectedTo = to.filter { !it.hasStack() }

    init {
        // ToDo: Needs smart code to move as efficient as possible.
        //  Also should handle overflow etc. Should be more generic
        listener<TickEvent.Pre> {
            selectedFrom.firstOrNull()?.let { from ->
                val preMove = from.stack.copy()

//                selectedTo.firstOrNull()?.let { to ->
//                    clickSlot(from.id, 0, SlotActionType.PICKUP)
//                    clickSlot(to.id, 0, SlotActionType.PICKUP)
//                    // ToDo: Handle overflow of cursor
//                }

                // ToDo: SWAP triangle
                val handler = player.currentScreenHandler
                handler.inventorySlots.firstOrNull {
                    it.stack.item.block in TaskFlow.disposables || it.stack.isEmpty
                }?.let { emptySlot ->
                    clickSlot(emptySlot.id, 0, SlotActionType.SWAP)
                    clickSlot(from.id, 0, SlotActionType.SWAP)
                }
                moved.add(preMove)
            } ?: finish()
        }

        listener<TickEvent.Post> {
            if (selectedFrom.isEmpty() || moved.sumOf { it.count } >= selection.count) {
                finish()
            }
        }
    }

    private fun SafeContext.finish() {
        if (closeScreen) player.closeHandledScreen()
        success(moved)
    }

    companion object {
        @Ta5kBuilder
        inline fun <reified H : ScreenHandler> moveItems(
            screen: H,
            selection: StackSelection,
            from: List<Slot>,
            to: List<Slot>,
        ) = InventoryTask(screen, selection, from, to)

        @Ta5kBuilder
        inline fun <reified H : ScreenHandler> withdraw(screen: H, selection: StackSelection) =
            moveItems(screen, selection, screen.containerSlots, screen.inventorySlots)

        @Ta5kBuilder
        inline fun <reified H : ScreenHandler> deposit(screen: H, selection: StackSelection) =
            moveItems(screen, selection, screen.inventorySlots, screen.containerSlots)
    }
}
