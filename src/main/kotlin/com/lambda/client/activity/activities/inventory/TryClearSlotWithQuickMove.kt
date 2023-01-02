package com.lambda.client.activity.activities.inventory

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import net.minecraft.inventory.Slot

class TryClearSlotWithQuickMove(
    private val slot: Slot
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        if (slot.stack.isEmpty) {
            success()
        } else {
            addSubActivities(
                QuickMoveSlot(slot).also {
                    executeOnSuccess = {
                        if (slot.stack.isEmpty) {
                            success()
                        } else {
                            failedWith(ExceptionSlotNotEmpty())
                        }
                    }
                }
            )
        }
    }

    class ExceptionSlotNotEmpty : Exception("Slot is still not empty")
}