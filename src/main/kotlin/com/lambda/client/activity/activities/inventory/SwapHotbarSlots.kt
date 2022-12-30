package com.lambda.client.activity.activities.inventory

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.CompoundActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.HotbarSlot
import net.minecraft.inventory.ClickType

class SwapHotbarSlots(
    private val slotFrom: HotbarSlot,
    private val slotTo: HotbarSlot
) : CompoundActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        addSubActivities(InventoryTransaction(player.openContainer.windowId, slotFrom.slotNumber, slotTo.hotbarSlot, ClickType.SWAP))
    }
}