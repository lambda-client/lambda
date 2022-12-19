package com.lambda.client.manager.managers.activity.activities.inventory

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.InstantActivity
import net.minecraft.inventory.ClickType
import net.minecraft.inventory.Slot

class DumpSlot(private val slot: Slot) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        addSubActivities(InventoryTransaction(0, slot.slotNumber, 1, ClickType.THROW))
    }
}