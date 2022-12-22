package com.lambda.client.activity.activities.inventory

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.InstantActivity
import com.lambda.client.activity.activities.storage.ExtractItemFromShulkerBox
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.ActivityManager.addSubActivities
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
            addSubActivities(SwapOrSwitchToSlot(slotFrom, predicateSlot))
        } ?: run {
            addSubActivities(ExtractItemFromShulkerBox(item, 0, predicateItem, predicateSlot))
        }
    }
}