package com.lambda.client.manager.managers.activity.activities.inventory

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.InstantActivity
import com.lambda.client.util.items.HotbarSlot
import net.minecraft.inventory.ClickType

class SwapHotbarSlots(
    private val slotFrom: HotbarSlot,
    private val slotTo: HotbarSlot
) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        subActivities.add(InventoryTransaction(0, slotFrom.slotNumber, slotTo.hotbarSlot, ClickType.SWAP))
    }
}