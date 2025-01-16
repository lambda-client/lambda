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

package com.lambda.interaction.material.transfer

import com.lambda.Lambda.LOG
import com.lambda.config.groups.InventoryConfig
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.transfer.TransactionExecutor.Companion.transfer
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.task.Task
import com.lambda.util.extension.containerSlots
import com.lambda.util.extension.inventorySlots
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot

class SlotTransfer @Ta5kBuilder constructor(
    val screen: ScreenHandler,
    private val selection: StackSelection,
    val from: List<Slot>,
    val to: List<Slot>,
    private val closeScreen: Boolean = true,
    private val settings: InventoryConfig = TaskFlowModule.inventory,
) : Task<Unit>() {
    private var selectedFrom = selection.filterSlots(from)
    private var selectedTo =
        to.filter { it.stack.isEmpty } // + to.filter { it.stack.item.block in TaskFlowModule.disposables }
    override val name: String
        get() = "Moving $selection from slots [${selectedFrom.joinToString { "${it.id}" }}] to slots [${selectedTo.joinToString { "${it.id}" }}] in ${screen::class.simpleName}"

    private var delay = 0
    private lateinit var changes: InventoryChanges

    override fun SafeContext.onStart() {
        changes = InventoryChanges(player.currentScreenHandler.slots)
    }

    init {
        listen<TickEvent.Pre> {
            val current = player.currentScreenHandler
            if (current != screen) {
                failure("Screen has changed. Expected ${screen::class.simpleName} (revision ${screen.revision}, got ${current::class.simpleName} (revision ${current.revision})")
                return@listen
            }

            if (changes.fulfillsSelection(to, selection)) {
                if (closeScreen) player.closeHandledScreen()
                success()
                return@listen
            }

            if (--delay >= 0) return@listen
            delay = settings.actionTimout

            selectedFrom = selection.filterSlots(from)
            selectedTo =
                to.filter { it.stack.isEmpty } // + to.filter { it.stack.item.block in TaskFlowModule.disposables }

            val nextFrom = selectedFrom.firstOrNull() ?: return@listen
            val nextTo = selectedTo.firstOrNull() ?: return@listen

            LOG.info("Changes so far:\n$changes")

            transfer {
                moveSlot(nextFrom.id, nextTo.id)
            }.finally { change ->
                changes merge change
            }.execute(this@SlotTransfer)

//            if (transfer.fulfillsSelection(selection)) {
//                info("Transfer complete")
////                success()
////                if (closeScreen) player.closeHandledScreen()
//                return@listen
//            }
        }
    }

    companion object {
        @Ta5kBuilder
        fun moveItems(
            screen: ScreenHandler,
            selection: StackSelection,
            from: List<Slot>,
            to: List<Slot>,
            closeScreen: Boolean = true,
        ) = SlotTransfer(screen, selection, from, to, closeScreen)

        @Ta5kBuilder
        fun withdraw(screen: ScreenHandler, selection: StackSelection, closeScreen: Boolean = true) =
            moveItems(screen, selection, screen.containerSlots, screen.inventorySlots, closeScreen)

        @Ta5kBuilder
        fun deposit(screen: ScreenHandler, selection: StackSelection, closeScreen: Boolean = true) =
            moveItems(screen, selection, screen.inventorySlots, screen.containerSlots, closeScreen)
    }
}
