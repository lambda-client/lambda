package com.lambda.client.manager.managers.activity.activities.storage

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.InstantActivity
import com.lambda.client.manager.managers.activity.activities.TimeoutActivity
import com.lambda.client.manager.managers.activity.activities.inventory.InventoryTransactionActivity
import net.minecraft.inventory.ClickType
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class PullItemFromContainerActivity(
    private val item: Item,
    private val predicateItem: (ItemStack) -> Boolean = { true },
    override var timeout: Long = 100L,
    override var creationTime: Long = 0L
) : InstantActivity, TimeoutActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        player.openContainer.inventorySlots.filter { slot ->
            slot.stack.item == item && predicateItem(slot.stack)
        }.forEach {
            timeout += 100L
            subActivities.add(InventoryTransactionActivity(
                player.openContainer.windowId,
                it.slotNumber,
                0,
                ClickType.QUICK_MOVE
            ))
        }
    }
}