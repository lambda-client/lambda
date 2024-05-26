package com.lambda.task.tasks

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task
import com.lambda.task.TaskCha1nBuilder
import com.lambda.util.item.ItemStackUtils.equal
import com.lambda.util.primitives.extension.containerSlots
import com.lambda.util.primitives.extension.inventorySlots
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType

class InventoryTask<H : ScreenHandler>(
    val screen: H,
    val from: List<Slot>,
    private val closeScreen: Boolean = true
) : Task<List<ItemStack>>() {
    private val moved = mutableListOf<ItemStack>()

    init {
        // ToDo: Needs smart code to move as efficient as possible.
        //  Also should handle overflow etc. Should be more generic
        listener<TickEvent.Pre> {
            from.firstOrNull { it.hasStack() }?.let {
                val preMove = it.stack.copy()
                // ToDo: Quickmove will not work in most situations
                screen.onSlotClick(it.index, 0, SlotActionType.QUICK_MOVE, player)
                if (!it.stack.equal(preMove)) {
                    moved.add(preMove)
                }
//                screen.syncState() // ToDo: Needed?
            } ?: run {
                if (closeScreen) player.closeHandledScreen()
                success(moved)
            }
        }
    }

    companion object {
        @TaskCha1nBuilder
        inline fun <reified H : ScreenHandler> withdraw(screen: H, selection: StackSelection) =
            InventoryTask(
                screen,
                selection.filterSlots(screen.containerSlots)
            )

        @TaskCha1nBuilder
        inline fun <reified H : ScreenHandler> deposit(screen: H, selection: StackSelection) =
            InventoryTask(
                screen,
                selection.filterSlots(screen.inventorySlots)
            )
    }
}