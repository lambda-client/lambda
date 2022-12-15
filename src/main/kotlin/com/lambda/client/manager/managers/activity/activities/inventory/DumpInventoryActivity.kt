package com.lambda.client.manager.managers.activity.activities.inventory

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.types.InstantActivity
import com.lambda.client.util.items.allSlots

class DumpInventoryActivity(private val amount: Int = 0) : InstantActivity() {
    override fun SafeClientEvent.onInitialize() {
        player.allSlots
            .filter { it.hasStack }
            .forEach {
                subActivities.add(DumpSlotActivity(it, amount))
            }
    }
}