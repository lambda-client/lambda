package com.lambda.client.activity.activities.inventory

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.InstantActivity
import com.lambda.client.event.SafeClientEvent
import net.minecraft.inventory.ClickType
import net.minecraft.inventory.Slot

class DumpSlot(private val slot: Slot) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        addSubActivities(InventoryTransaction(player.openContainer.windowId, slot.slotNumber, 1, ClickType.THROW))
    }
}