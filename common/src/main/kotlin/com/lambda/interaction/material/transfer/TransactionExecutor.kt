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

import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.material.transfer.transaction.*
import com.lambda.task.Task
import net.minecraft.screen.slot.SlotActionType

class TransactionExecutor @Ta5kBuilder constructor(
    private val transactions: MutableList<InventoryTransaction> = mutableListOf(),
) : Task<InventoryChanges>() {
    override val name: String get() = "Execution of ${transactions.size} transactions left"

    private lateinit var changes: InventoryChanges

    override fun SafeContext.onStart() {
        changes = InventoryChanges(player.currentScreenHandler.slots)
    }

    init {
        listen<TickEvent.Pre> {
            if (transactions.isEmpty()) {
                success(changes)
                return@listen
            }

            transactions.removeFirstOrNull()?.finally { change ->
                changes merge change
            }?.execute(this@TransactionExecutor)
        }
    }

    @DslMarker
    annotation class InvTransfer

    @InvTransfer
    fun click(slotId: Int, button: Int, actionType: SlotActionType) {
        transactions.add(ClickSlotTransaction(slotId, button, actionType))
    }

    @InvTransfer
    fun pickFromInventory(slotId: Int) {
        transactions.add(PickFromInventoryTransaction(slotId))
    }

    @InvTransfer
    fun dropItemInHand(entireStack: Boolean = true) {
        transactions.add(DropItemInHandTransaction(entireStack))
    }

    @InvTransfer
    fun swapHands() {
        transactions.add(SwapHandsTransaction())
    }

    @InvTransfer
    fun swapToHotbarSlot(slotId: Int) {
        transactions.add(SwapHotbarSlotTransaction(slotId))
    }

    @InvTransfer
    fun pickup(slotId: Int, button: Int = 0) = click(slotId, button, SlotActionType.PICKUP)

    // Quick move action (Shift-click)
    @InvTransfer
    fun quickMove(slotId: Int) = click(slotId, 0, SlotActionType.QUICK_MOVE)

    @InvTransfer
    fun swap(slotId: Int, hotbarSlot: Int) = click(slotId, hotbarSlot, SlotActionType.SWAP)

    // Clone action (Creative mode)
    @InvTransfer
    fun clone(slotId: Int) = click(slotId, 2, SlotActionType.CLONE)

    // Throw stack or single item
    @InvTransfer
    fun throwStack(slotId: Int) = click(slotId, 1, SlotActionType.THROW)

    @InvTransfer
    fun throwSingle(slotId: Int) = click(slotId, 0, SlotActionType.THROW)

    // Quick craft action
    @InvTransfer
    fun quickCraftStart(slotId: Int) = click(slotId, 0, SlotActionType.QUICK_CRAFT)

    @InvTransfer
    fun quickCraftDrag(slotId: Int) = click(slotId, 1, SlotActionType.QUICK_CRAFT)

    @InvTransfer
    fun quickCraftEnd(slotId: Int) = click(slotId, 2, SlotActionType.QUICK_CRAFT)

    // Pickup all items (double-click)
    @InvTransfer
    fun pickupAll(slotId: Int) = click(slotId, 0, SlotActionType.PICKUP_ALL)

    // Helper function: Move items from one slot to another
    @InvTransfer
    fun moveSlot(fromSlotId: Int, toSlotId: Int, button: Int = 0) {
        pickup(fromSlotId, button)
        pickup(toSlotId, button)
    }

    // Helper function: Split a stack into two
    @InvTransfer
    fun splitStack(slotId: Int, targetSlotId: Int) {
        pickup(slotId, 1) // Pickup half the stack
        pickup(targetSlotId, 0) // Place it in the target slot
    }

    // Helper function: Merge stacks
    @InvTransfer
    fun mergeStacks(sourceSlotId: Int, targetSlotId: Int) {
        pickup(sourceSlotId, 0)
        pickup(targetSlotId, 0)
    }

    companion object {
        @InvTransfer
        fun transfer(block: TransactionExecutor.() -> Unit) =
            TransactionExecutor().apply {
                block(this)
            }
    }
}