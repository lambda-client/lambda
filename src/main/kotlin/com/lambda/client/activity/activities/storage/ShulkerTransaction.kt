package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.construction.core.BreakBlock
import com.lambda.client.activity.activities.storage.core.CloseContainer
import com.lambda.client.activity.activities.storage.core.ContainerWindowTransaction
import com.lambda.client.activity.activities.storage.core.PlaceContainer
import com.lambda.client.activity.activities.storage.types.ContainerAction
import com.lambda.client.activity.activities.storage.types.ItemOrder
import com.lambda.client.activity.activities.storage.types.ShulkerOrder
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.allSlots

/**
 * Push or pull item from a fitting shulker from inventory
 */
class ShulkerTransaction(
    val order: ShulkerOrder
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        if (order.action == ContainerAction.PULL) {
            player.allSlots.mapNotNull {
                order.findShulkerToPull(it)
            }.minByOrNull { it.second }?.first?.let { slot ->
                addSubActivities(
                    PlaceContainer(slot.stack.copy(), open = true)
                )
                return
            }
        } else {
            player.allSlots.mapNotNull {
                order.findShulkerToPush(it)
            }.minByOrNull { it.second }?.first?.let { slot ->
                addSubActivities(
                    PlaceContainer(slot.stack.copy(), open = true)
                )
                return
            }
        }
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        if (childActivity !is PlaceContainer) return

        addSubActivities(
            ContainerWindowTransaction(ItemOrder(order.action, order.item, order.amount)),
            CloseContainer(),
            BreakBlock(
                childActivity.containerPos,
                collectDrops = true,
                ignoreIgnored = true
            )
        )
    }
}