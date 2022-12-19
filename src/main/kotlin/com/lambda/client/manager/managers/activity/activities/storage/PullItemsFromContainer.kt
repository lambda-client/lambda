package com.lambda.client.manager.managers.activity.activities.storage

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.InstantActivity
import com.lambda.client.manager.managers.activity.activities.inventory.InventoryTransaction
import com.lambda.client.util.items.countEmpty
import com.lambda.client.util.items.inventorySlots
import net.minecraft.inventory.ClickType
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class PullItemsFromContainer(
    private val item: Item,
    private val amount: Int, // 0 = all
    private val predicateItem: (ItemStack) -> Boolean = { true }
) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        val maxEmpty = player.inventorySlots.countEmpty() - 1

        val take = if (amount > 0) amount.coerceAtMost(maxEmpty) else maxEmpty

        player.openContainer.inventorySlots.filter { slot ->
            slot.stack.item == item && predicateItem(slot.stack)
        }.take(take).forEach {
            subActivities.add(InventoryTransaction(
                player.openContainer.windowId,
                it.slotNumber,
                0,
                ClickType.QUICK_MOVE
            ))
        }
    }
}