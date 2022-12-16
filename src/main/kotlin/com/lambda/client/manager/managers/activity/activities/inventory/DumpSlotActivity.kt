package com.lambda.client.manager.managers.activity.activities.inventory

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.InstantActivity
import net.minecraft.inventory.ClickType
import net.minecraft.inventory.Slot

class DumpSlotActivity(private val slot: Slot) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        subActivities.add(InventoryTransactionActivity(0, slot.slotNumber, 1, ClickType.THROW))
    }
}