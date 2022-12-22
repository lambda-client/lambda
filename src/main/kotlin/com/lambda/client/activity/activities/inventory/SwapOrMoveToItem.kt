package com.lambda.client.activity.activities.inventory

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.InstantActivity
import com.lambda.client.activity.activities.storage.ExtractItemFromShulkerBox
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.allSlots
import com.lambda.client.util.items.hotbarSlots
import com.lambda.client.util.text.MessageSendHelper
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class SwapOrMoveToItem(
    private val item: Item,
    private val predicateItem: (ItemStack) -> Boolean = { true },
    private val predicateSlot: (ItemStack) -> Boolean = { true },
    private val useShulkerBoxes: Boolean = true,
    private val useEnderChest: Boolean = false
) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        player.hotbarSlots.firstOrNull { slot ->
            slot.stack.item == item && predicateItem(slot.stack)
        }?.let {
            addSubActivities(SwitchToHotbarSlot(it))
        } ?: run {
            player.allSlots.firstOrNull { slot ->
                slot.stack.item == item && predicateItem(slot.stack)
            }?.let { slotFrom ->
                addSubActivities(SwapOrSwitchToSlot(slotFrom, predicateSlot))
            } ?: run {
                if (useShulkerBoxes) {
                    addSubActivities(ExtractItemFromShulkerBox(item, 1, predicateItem, predicateSlot))
                } else {
                    activityStatus = ActivityStatus.FAILURE
                    MessageSendHelper.sendErrorMessage("No $item found in inventory (shulkers are disabled)")
                }
            }
        }
    }
}