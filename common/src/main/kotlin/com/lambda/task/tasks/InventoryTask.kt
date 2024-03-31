package com.lambda.task.tasks

import com.lambda.interaction.material.StackSelection
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.Task
import com.lambda.task.TaskCha1nBuilder
import com.lambda.task.TaskChainBuilder
import com.lambda.threading.runGameBlocking
import com.lambda.util.item.ItemStackUtils.equal
import com.lambda.util.primitives.extension.containerSlots
import com.lambda.util.primitives.extension.inventorySlots
import kotlinx.coroutines.delay
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType

class InventoryTask<H : ScreenHandler>(
    val screen: H,
    val from: List<Slot>,
    private val closeScreen: Boolean = true
) : Task<List<ItemStack>>() {

    // ToDo: Needs smart code to move as efficient as possible.
    //  Also should handle overflow etc. Should be more generic
    override suspend fun onAction(): List<ItemStack> {
        val moved = mutableListOf<ItemStack>()

        from.forEach {
            runGameBlocking {
                // ToDo: Qickmove will not work in most situations
                val preMove = it.stack.copy()
                screen.onSlotClick(it.index, 0, SlotActionType.QUICK_MOVE, player)
                if (!it.stack.equal(preMove)) {
                    moved.add(preMove)
                }
            }
            delay(TaskFlow.itemMoveDelay)
//                screen.syncState() // ToDo: Needed?
        }
        runGameBlocking {
            if (closeScreen) player.closeHandledScreen()
        }

        return moved
    }

    companion object {
        @TaskCha1nBuilder
        inline fun <reified H : ScreenHandler> TaskChainBuilder.withdraw(screen: H, selection: StackSelection) =
            InventoryTask(
                screen,
                selection.filterSlots(screen.containerSlots)
            ).apply {
                required(this)
            }

        @TaskCha1nBuilder
        inline fun <reified H : ScreenHandler> TaskChainBuilder.deposit(screen: H, selection: StackSelection) =
            InventoryTask(
                screen,
                selection.filterSlots(screen.inventorySlots)
            ).apply {
                required(this)
            }
    }
}