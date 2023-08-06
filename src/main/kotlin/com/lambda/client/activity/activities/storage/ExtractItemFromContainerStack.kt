package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.construction.core.BreakBlock
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.storage.core.CloseContainer
import com.lambda.client.activity.activities.storage.core.PlaceContainer
import com.lambda.client.activity.activities.storage.core.ContainerTransaction
import com.lambda.client.activity.activities.travel.CollectDrops
import com.lambda.client.event.SafeClientEvent
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack

class ExtractItemFromContainerStack(
    containerStack: ItemStack,
    private val itemInfo: ItemInfo
) : Activity() {
    private val containerStack = containerStack.copy()

    override fun SafeClientEvent.onInitialize() {
        addSubActivities(
            PlaceContainer(containerStack, open = true)
        )
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        if (childActivity !is PlaceContainer) return

        addSubActivities(
            ContainerTransaction(Order(Action.PULL, itemInfo)),
            CloseContainer(),
            BreakBlock(
                childActivity.containerPos,
                forceSilk = containerStack.item == Blocks.ENDER_CHEST,
                ignoreIgnored = true
            ),
            CollectDrops(containerStack.item),
            AcquireItemInActiveHand(itemInfo)
        )
    }
}