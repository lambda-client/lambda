package com.lambda.client.manager.managers.activity.activities.inventory

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.types.InstantActivity
import net.minecraft.inventory.ClickType
import net.minecraft.inventory.Slot

class DumpSlotActivity(private val slot: Slot, private val amount: Int) : InstantActivity() {
    override fun SafeClientEvent.onInitialize() {
        if (slot.stack.count > amount || amount == 0) {
            subActivities.add(InventoryTransactionActivity(0, slot.slotIndex, 1, ClickType.THROW))
        } else {
            repeat(amount) {
                subActivities.add(InventoryTransactionActivity(0, slot.slotNumber, 0, ClickType.THROW))
            }
        }
    }
}