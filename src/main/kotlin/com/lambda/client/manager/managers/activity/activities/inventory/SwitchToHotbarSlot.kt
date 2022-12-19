package com.lambda.client.manager.managers.activity.activities.inventory

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.InstantActivity
import com.lambda.client.util.items.HotbarSlot

class SwitchToHotbarSlot(private val slot: HotbarSlot) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        if (slot.hotbarSlot !in 0..8) return
        player.inventory.currentItem = slot.hotbarSlot
        playerController.updateController()
    }
}