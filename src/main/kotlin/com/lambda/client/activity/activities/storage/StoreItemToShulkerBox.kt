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
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class StoreItemToShulkerBox( // TODO: Add support for multiple shulker boxes
    val item: Item,
    private val amount: Int = 0, // 0 = all
    private val predicateStack: (ItemStack) -> Boolean = { true }
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        val candidates = mutableMapOf<Slot, Int>()

        if (player.allSlots.countItem(item) == 0) {
            success()
            return
        }

        player.allSlots.forEach { slot ->
            getShulkerInventory(slot.stack)?.let { inventory ->
                if (inventory.all { (it.item == item && predicateStack(it)) || it.isEmpty }) {
                    val count = inventory.count { it.item == item && predicateStack(it) }

                    if (count < 27) candidates[slot] = count
                }
            }
        }

        if (candidates.isEmpty()) {
            failedWith(NoShulkerBoxFoundStoreException(item))
            return
        }

        candidates.maxBy { it.value }.key.let { slot ->
            addSubActivities(
                PlaceContainer(slot.stack.copy(), open = true)
            )
        }
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        if (childActivity !is PlaceContainer) return

        addSubActivities(
            ContainerTransaction(
                ContainerTransaction.Order(
                    ContainerTransaction.Action.PUSH,
                    item,
                    amount,
                    predicateStack
                )
            ),
            CloseContainer(),
            BreakBlock(childActivity.containerPos, collectDrops = true)
        )
    }

    class NoShulkerBoxFoundStoreException(item: Item) : Exception("No shulker box was found with space to store ${item.registryName}")
}