package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.interaction.BreakBlock
import com.lambda.client.activity.activities.interaction.CloseContainer
import com.lambda.client.activity.activities.interaction.OpenContainer
import com.lambda.client.activity.activities.inventory.SwapOrSwitchToSlot
import com.lambda.client.activity.activities.utils.getShulkerInventory
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.allSlots
import com.lambda.client.util.items.block
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class StoreItemToShulkerBox( // TODO: Add support for multiple shulker boxes
    private val item: Item,
    private val amount: Int = 0, // 0 = all
    private val predicateItem: (ItemStack) -> Boolean = { true }
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        val candidates = mutableMapOf<Slot, Int>()

        player.allSlots.forEach { slot ->
            getShulkerInventory(slot.stack)?.let { inventory ->
                if (inventory.all { (it.item == item && predicateItem(it)) || it.isEmpty }) {
                    val count = inventory.count { it.item == item && predicateItem(it) }

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
                SwapOrSwitchToSlot(slot),
                PlaceContainer(slot.stack.item.block.defaultState)
            )
        }
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        if (childActivity !is PlaceContainer) return

        addSubActivities(
            OpenContainer(childActivity.containerPos),
            PushItemsToContainer(item, amount, predicateItem),
            CloseContainer(),
            BreakBlock(childActivity.containerPos, collectDrops = true)
        )
    }

    class NoShulkerBoxFoundStoreException(item: Item) : Exception("No shulker box was found with space to store ${item.registryName}")
}