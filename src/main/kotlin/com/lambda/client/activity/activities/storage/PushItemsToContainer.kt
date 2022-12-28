package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.InstantActivity
import com.lambda.client.activity.activities.inventory.QuickMoveSlot
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.getSlots
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class PushItemsToContainer(
    private val item: Item,
    private val amount: Int = 0, // 0 = all
    private val predicateItem: (ItemStack) -> Boolean = { true }
) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        val openContainer = player.openContainer

        if (openContainer.inventorySlots.size != 63) return

        openContainer.getSlots(27..62).filter { slot ->
            slot.stack.item == item && predicateItem(slot.stack)
        }.forEach {
            addSubActivities(QuickMoveSlot(it))
        }
    }
}