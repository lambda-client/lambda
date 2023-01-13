package com.lambda.client.activity.activities.inventory

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.HotbarSlot
import net.minecraft.inventory.ClickType
import net.minecraft.inventory.Slot

class SwapWithSlot(
    private val slotFrom: Slot,
    private val hotbarSlotTo: Int
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        addSubActivities(InventoryTransaction(player.openContainer.windowId, slotFrom.slotNumber, hotbarSlotTo, ClickType.SWAP))
    }
}