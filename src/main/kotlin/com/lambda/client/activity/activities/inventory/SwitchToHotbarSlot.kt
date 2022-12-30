package com.lambda.client.activity.activities.inventory

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.HotbarSlot

class SwitchToHotbarSlot(private val slot: HotbarSlot) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        if (slot.hotbarSlot !in 0..8) return
        player.inventory.currentItem = slot.hotbarSlot
        playerController.updateController()
        onSuccess()
    }
}