package com.lambda.client.manager.managers.activity.activities.inventory

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.InstantActivity
import com.lambda.client.util.items.allSlots

class DumpInventory() : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        player.allSlots
            .filter { it.hasStack }
            .forEach {
                subActivities.add(DumpSlot(it))
            }
    }
}