package com.lambda.client.activity.activities.inventory

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.allSlots

class DumpInventory : Activity() {
    override fun SafeClientEvent.onInitialize() {
        player.allSlots
            .filter { it.hasStack }
            .forEach {
                addSubActivities(DumpSlot(it))
            }
    }
}