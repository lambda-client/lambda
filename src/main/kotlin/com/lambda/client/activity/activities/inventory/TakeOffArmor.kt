package com.lambda.client.activity.activities.inventory

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.armorSlots
import com.lambda.client.util.items.offhandSlot

class TakeOffArmor : Activity() {
    override fun SafeClientEvent.onInitialize() {
        (player.armorSlots + player.offhandSlot).forEach { slot ->
            if (slot.stack.isEmpty) return@forEach

            addSubActivities(TryClearSlotWithQuickMove(slot))
        }
    }
}