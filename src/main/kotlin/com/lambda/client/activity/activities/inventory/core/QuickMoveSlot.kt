package com.lambda.client.activity.activities.inventory.core

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import net.minecraft.inventory.ClickType
import net.minecraft.inventory.Slot

class QuickMoveSlot(
    private val slot: Slot
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        if (slot.stack.isEmpty) {
            success()
        } else {
            addSubActivities(InventoryTransaction(player.openContainer.windowId, slot.slotNumber, 0, ClickType.QUICK_MOVE))
        }
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        if (childActivity !is InventoryTransaction) return

        if (slot.stack.isEmpty) {
            success()
        } else {
            failedWith(ExceptionSlotNotEmpty())
        }
    }

    override fun SafeClientEvent.onChildFailure(childActivities: ArrayDeque<Activity>, childException: Exception): Boolean {
        if (childException !is InventoryTransaction.DeniedException) return false

        failedWith(ExceptionSlotNotEmpty())
        return true
    }

    class ExceptionSlotNotEmpty : Exception("Slot not empty")
}