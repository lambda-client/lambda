package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.construction.core.BreakBlock
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.storage.core.CloseContainer
import com.lambda.client.activity.activities.storage.core.PlaceContainer
import com.lambda.client.activity.activities.storage.core.ContainerTransaction
import com.lambda.client.activity.activities.travel.PickUpDrops
import com.lambda.client.event.SafeClientEvent
import net.minecraft.item.ItemStack

class ExtractItemFromShulkerBox(
    shulkerBoxStack: ItemStack,
    private val order: ContainerTransaction.Order
) : Activity() {
    private val stack = shulkerBoxStack.copy()

    override fun SafeClientEvent.onInitialize() {
        addSubActivities(
            PlaceContainer(stack, open = true)
        )
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        if (childActivity !is PlaceContainer) return

        addSubActivities(
            ContainerTransaction(order),
            CloseContainer(),
            BreakBlock(childActivity.containerPos),
            PickUpDrops(stack.item), // BreakBlock doesn't collect drops
            AcquireItemInActiveHand(order.item, order.predicateStack, order.predicateSlot)
        )
    }
}