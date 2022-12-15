package com.lambda.client.manager.managers.activity.activities.inventory

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.types.InstantActivity
import com.lambda.client.util.items.allSlots
import net.minecraft.inventory.ClickType

class DumpInventoryActivity : InstantActivity() {
    override fun SafeClientEvent.onInitialize() {
        player.allSlots
            .filter { it.hasStack }
            .forEach {
                subActivities.add(InventoryTransactionActivity(0, it.slotNumber, 1, ClickType.THROW))
            }
    }
}