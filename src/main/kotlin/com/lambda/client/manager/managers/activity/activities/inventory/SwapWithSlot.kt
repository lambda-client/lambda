package com.lambda.client.manager.managers.activity.activities.inventory

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.InstantActivity
import com.lambda.client.util.items.HotbarSlot
import net.minecraft.inventory.ClickType
import net.minecraft.inventory.Slot

class SwapWithSlot(
    private val slotFrom: Slot,
    private val slotTo: HotbarSlot
) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        addSubActivities(InventoryTransaction(0, slotFrom.slotIndex, slotTo.hotbarSlot, ClickType.SWAP))
    }
}