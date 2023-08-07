package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.construction.core.BreakBlock
import com.lambda.client.activity.activities.storage.core.CloseContainer
import com.lambda.client.activity.activities.storage.core.ContainerTransaction
import com.lambda.client.activity.activities.storage.core.PlaceContainer
import com.lambda.client.activity.getShulkerInventory
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.allSlots
import com.lambda.client.util.items.countItem
import net.minecraft.item.Item

class StoreItemToShulkerBox( // TODO: Add support for multiple shulker boxes
    private val itemInfo: ItemInfo
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        if (player.allSlots.countItem(itemInfo.item) == 0) {
            success()
            return
        }

        player.allSlots.mapNotNull { slot ->
            getShulkerInventory(slot.stack)?.let { inventory ->
                if (inventory.all { itemInfo.stackFilter(it) || it.isEmpty }) {
                    val count = inventory.count { itemInfo.stackFilter(it) }

                    if (count < 27) slot to count else null
                } else null
            }
        }.maxByOrNull { it.second }?.first?.let { slot ->
            addSubActivities(
                PlaceContainer(slot.stack.copy(), open = true)
            )
            return
        }

        failedWith(NoShulkerBoxFoundStoreException(itemInfo.item))
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        if (childActivity !is PlaceContainer) return

        addSubActivities(
            ContainerTransaction(Order(Action.PUSH, itemInfo)),
            CloseContainer(),
            BreakBlock(
                childActivity.containerPos,
                collectDrops = true,
                ignoreIgnored = true
            )
        )
    }

    class NoShulkerBoxFoundStoreException(item: Item) : Exception("No shulker box was found with space to store ${item.registryName}")
}