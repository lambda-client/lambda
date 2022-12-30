package com.lambda.client.activity.activities.inventory

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import net.minecraft.inventory.Slot

class TryClearSlotWithQuickMove(
    private val slot: Slot
) : Activity() {

    override fun SafeClientEvent.onInitialize() {
        if (slot.stack.isEmpty) {
            onSuccess()
        } else {
            addSubActivities(
                QuickMoveSlot(slot),
                SlotMustBeEmpty(slot)
            )
        }
    }

}