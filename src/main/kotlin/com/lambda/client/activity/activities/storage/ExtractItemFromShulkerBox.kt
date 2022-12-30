package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.interaction.CloseContainer
import com.lambda.client.activity.activities.inventory.SwapOrMoveToItem
import com.lambda.client.activity.activities.utils.getContainerPos
import com.lambda.client.activity.activities.utils.getShulkerInventory
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.allSlots
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class ExtractItemFromShulkerBox(
    private val item: Item,
    private val amount: Int = 0, // 0 = all
    private val predicateItem: (ItemStack) -> Boolean = { true },
    private val predicateSlot: (ItemStack) -> Boolean = { true }
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
//        if (player.inventorySlots.item)

        val candidates = mutableMapOf<Slot, Int>()

        player.allSlots.forEach { slot ->
            getShulkerInventory(slot.stack)?.let { inventory ->
                val count = inventory.count { it.item == item && predicateItem(it) }

                if (count > 0) candidates[slot] = count
            }
        }

        if (candidates.isEmpty()) return

        candidates.minBy { it.value }.key.let { slot ->
            getContainerPos()?.let { containerPos ->
                addSubActivities(
                    OpenContainerInSlot(slot),
                    PullItemsFromContainer(item, amount, predicateItem),
                    CloseContainer(),
                    BreakAndCollectShulker(containerPos),
                    SwapOrMoveToItem(item, predicateItem, predicateSlot)
                )
            }
        }
    }
}