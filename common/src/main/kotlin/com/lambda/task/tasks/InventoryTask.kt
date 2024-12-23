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

package com.lambda.task.tasks

import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.material.StackSelection
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.task.Task
import com.lambda.util.extension.containerSlots
import com.lambda.util.extension.inventorySlots
import com.lambda.util.item.ItemUtils.block
import com.lambda.util.player.SlotUtils
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType

class InventoryTask @Ta5kBuilder constructor(
    val screen: ScreenHandler,
    private val selection: StackSelection,
    val from: List<Slot>,
    val to: List<Slot>,
    private val closeScreen: Boolean = true
) : Task<Unit>() {
    override val name: String
        get() = "Moving $selection from [${from.joinToString { "${it.id}" }}] to [${from.joinToString { "${it.id}" }}] in ${runCatching { screen.type::class.simpleName }.getOrNull() ?: screen::class.simpleName}"

    private val transactions = mutableListOf<SlotUtils.Transaction>()

    override fun SafeContext.onStart() {
        val selectedFrom = selection.filterSlots(from).filter { it.hasStack() }
        val selectedTo = to.filter { it.stack.isEmpty } + to.filter { it.stack.item.block in TaskFlowModule.disposables }
        selectedFrom.zip(selectedTo).forEach { (from, to) ->
            transactions.add(SlotUtils.Transaction(to.index, 0, SlotActionType.SWAP))
            transactions.add(SlotUtils.Transaction(from.index, 0, SlotActionType.SWAP))

            // ToDo: Handle overflow of cursor for PICKUP
        }
    }

    init {
        // ToDo: Needs smart code to move as efficient as possible.
        //  Also should handle overflow etc. Should be more generic
        listen<TickEvent.Pre> {
            val moved = selection.filterSlots(to)
                .filter { it.hasStack() }
                .sumOf { it.stack.count } >= selection.count

            if (transactions.isEmpty() || moved) {
                if (closeScreen) player.closeHandledScreen()
                success()
            }

            transactions.removeFirstOrNull()?.click() ?: success()
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
        fun withdraw(screen: ScreenHandler, selection: StackSelection, closeScreen: Boolean = true) =
            moveItems(screen, selection, screen.containerSlots, screen.inventorySlots, closeScreen)

        @Ta5kBuilder
        fun deposit(screen: ScreenHandler, selection: StackSelection, closeScreen: Boolean = true) =
            moveItems(screen, selection, screen.inventorySlots, screen.containerSlots, closeScreen)
    }
}
