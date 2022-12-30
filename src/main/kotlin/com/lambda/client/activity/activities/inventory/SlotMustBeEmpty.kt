package com.lambda.client.activity.activities.inventory

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import net.minecraft.inventory.Slot

class SlotMustBeEmpty(
    private val slot: Slot
) : Activity() {

    override fun SafeClientEvent.onInitialize() {
        if (slot.stack.isEmpty) {
            onSuccess()
        } else {
            onFailure()
        }
    }
}