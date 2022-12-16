package com.lambda.client.manager.managers.activity.activities.inventory

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.InstantActivity
import com.lambda.client.util.items.HotbarSlot
import net.minecraft.inventory.ClickType
import net.minecraft.inventory.Slot

class SwapWithSlotActivity(
    private val slotFrom: Slot,
    private val slotTo: HotbarSlot
) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        subActivities.add(InventoryTransactionActivity(0, slotFrom.slotIndex, slotTo.hotbarSlot, ClickType.SWAP))
    }
}