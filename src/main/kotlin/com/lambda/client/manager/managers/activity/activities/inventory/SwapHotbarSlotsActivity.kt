package com.lambda.client.manager.managers.activity.activities.inventory

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.InstantActivity
import com.lambda.client.util.items.HotbarSlot
import net.minecraft.inventory.ClickType

class SwapHotbarSlotsActivity(
    private val slotFrom: Int,
    private val slotTo: Int
) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        subActivities.add(InventoryTransactionActivity(0, slotFrom, slotTo, ClickType.SWAP))
    }
}