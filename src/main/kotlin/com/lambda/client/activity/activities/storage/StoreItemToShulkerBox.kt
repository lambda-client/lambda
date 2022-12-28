package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.InstantActivity
import com.lambda.client.activity.activities.interaction.CloseContainer
import com.lambda.client.activity.activities.utils.getContainerPos
import com.lambda.client.activity.activities.utils.getShulkerInventory
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.allSlots
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class StoreItemToShulkerBox(
    private val item: Item,
    private val amount: Int = 0, // 0 = all
    private val predicateItem: (ItemStack) -> Boolean = { true }
) : InstantActivity, Activity() {
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

        if (candidates.isEmpty()) return

        candidates.maxBy { it.value }.key.let { slot ->
            getContainerPos()?.let { containerPos ->
                addSubActivities(
                    OpenContainerInSlot(slot),
                    PushItemsToContainer(item, amount, predicateItem),
                    CloseContainer(),
                    BreakAndCollectShulker(containerPos)
                )
            }
        }
    }
}