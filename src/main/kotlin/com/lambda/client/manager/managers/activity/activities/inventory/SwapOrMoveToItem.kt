package com.lambda.client.manager.managers.activity.activities.inventory

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.InstantActivity
import com.lambda.client.manager.managers.activity.activities.storage.ExtractItemFromShulkerBox
import com.lambda.client.util.items.*
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class SwapOrMoveToItem(
    private val item: Item,
    private val predicateItem: (ItemStack) -> Boolean = { true },
    private val predicateSlot: (ItemStack) -> Boolean = { true },
    private val useShulkerBoxes: Boolean = false,
    private val useEnderChest: Boolean = false
) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        player.allSlots.firstOrNull { slot ->
            slot.stack.item == item && predicateItem(slot.stack)
        }?.let { slotFrom ->
            subActivities.add(SwapOrSwitchToSlot(slotFrom, predicateSlot))
        } ?: run {
            subActivities.add(ExtractItemFromShulkerBox(item, 0, predicateItem, predicateSlot))
        }
    }
}