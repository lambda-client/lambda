package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.QuickMoveSlot
import com.lambda.client.activity.activities.types.LoopingUntilActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.allSlots
import com.lambda.client.util.items.countEmpty
import com.lambda.client.util.items.getSlots
import com.lambda.client.util.text.MessageSendHelper
import net.minecraft.inventory.ContainerShulkerBox
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class PushItemsToContainer(
    private val item: Item,
    private val amount: Int = 0, // 0 = all
    private val predicateItem: (ItemStack) -> Boolean = { true },
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        val openContainer = player.openContainer

        if (openContainer.inventorySlots.size != 63) return

        var candidateSlots = openContainer.getSlots(27..62).filter { slot ->
            slot.stack.item == item && predicateItem(slot.stack)
        }

        if (amount != 0) candidateSlots = candidateSlots.take(amount)

        candidateSlots.forEach {
            addSubActivities(QuickMoveSlot(it))
        }

//        if (subActivities.isEmpty()) {
//            failedWith(NoItemFoundPushException(item))
//        }
    }

    override fun SafeClientEvent.onChildFailure(childActivities: ArrayDeque<Activity>, childException: Exception): Boolean {
        if (childException !is QuickMoveSlot.ExceptionSlotNotEmpty) return false

        success()
        return true
    }

    class NotShulkerBoxException : Exception("No shulker box open")

//    class NoItemFoundPushException(item: Item) : Exception("No item found to push to container: ${item.registryName}")
}