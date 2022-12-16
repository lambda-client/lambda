package com.lambda.client.manager.managers.activity.activities.inventory

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.InstantActivity

class SwitchToHotbarSlotActivity(private val slot: Int) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        if (slot !in 0..8) return
        player.inventory.currentItem = slot
        playerController.updateController()
    }
}