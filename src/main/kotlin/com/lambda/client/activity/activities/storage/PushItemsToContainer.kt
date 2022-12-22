package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.InstantActivity
import com.lambda.client.activity.activities.inventory.InventoryTransaction
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.countEmpty
import com.lambda.client.util.items.getSlots
import com.lambda.client.util.items.inventorySlots
import net.minecraft.inventory.ClickType
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class PushItemsToContainer(
    private val item: Item,
    private val amount: Int = 0, // 0 = all
    private val predicateItem: (ItemStack) -> Boolean = { true }
) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
//        val maxEmpty = player.openContainer.inventorySlots.countEmpty()
//
//        val store = if (amount > 0) amount.coerceAtMost(maxEmpty) else maxEmpty

//        player.openContainer.getSlots(0..26)
//        player.openContainer.getSlots(27..62)

        player.openContainer.getSlots(27..62).filter { slot ->
            slot.stack.item == item && predicateItem(slot.stack)
        }.forEach {
            addSubActivities(InventoryTransaction(
                player.openContainer.windowId,
                it.slotNumber,
                0,
                ClickType.QUICK_MOVE
            ))
        }

//        player.inventorySlots.filter { slot ->
//            slot.stack.item == item && predicateItem(slot.stack)
//        }.take(store).forEach {
//            addSubActivities(InventoryTransaction(
//                player.openContainer.windowId,
//                it.slotNumber,
//                0,
//                ClickType.QUICK_MOVE
//            ))
//        }
    }
}