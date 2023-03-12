package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.construction.core.BreakBlock
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.storage.core.CloseContainer
import com.lambda.client.activity.activities.travel.PickUpDrops
import com.lambda.client.event.SafeClientEvent
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class ExtractItemFromShulkerBox(
    private val shulkerBoxStack: ItemStack,
    private val item: Item,
    private val predicateStack: (ItemStack) -> Boolean = { true },
    private val predicateSlot: (ItemStack) -> Boolean = { true },
    private var metadata: Int? = null,
    private val amount: Int = 0 // 0 = all
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
            PullItemsFromContainer(item, predicateStack, metadata, amount),
            CloseContainer(),
            BreakBlock(childActivity.containerPos),
            PickUpDrops(stack.item), // BreakBlock doesn't collect drops
            AcquireItemInActiveHand(item, predicateStack, predicateSlot)
        )
    }
}