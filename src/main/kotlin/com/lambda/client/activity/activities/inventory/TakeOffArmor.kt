package com.lambda.client.activity.activities.inventory

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.armorSlots
import com.lambda.client.util.items.offhandSlot
import net.minecraft.inventory.ClickType

class TakeOffArmor : Activity() {

    override fun SafeClientEvent.onInitialize() {
        player.armorSlots.forEach {
            addSubActivities(
                TryClearSlotWithQuickMove(it)
            )
        }
        addSubActivities(
            TryClearSlotWithQuickMove(player.offhandSlot)
        )
    }

}