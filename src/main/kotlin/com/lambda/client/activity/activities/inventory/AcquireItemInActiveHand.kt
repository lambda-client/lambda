package com.lambda.client.activity.activities.inventory

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.storage.ExtractItemFromShulkerBox
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.allSlots
import com.lambda.client.util.items.hotbarSlots
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumHand

class AcquireItemInActiveHand(
    private val item: Item,
    private val predicateItem: (ItemStack) -> Boolean = { true },
    private val predicateSlot: (ItemStack) -> Boolean = { true },
    private val useShulkerBoxes: Boolean = true,
    private val useEnderChest: Boolean = false
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        player.hotbarSlots.firstOrNull { slot ->
            slot.stack.item == item && predicateItem(slot.stack)
        }?.let { hotbarSlot ->
            addSubActivities(SwitchToHotbarSlot(hotbarSlot))
        } ?: run {
            player.allSlots.firstOrNull { slot ->
                slot.stack.item == item && predicateItem(slot.stack)
            }?.let { slotFrom ->
                addSubActivities(SwapOrSwitchToSlot(slotFrom, predicateSlot))
            } ?: run {
                if (useShulkerBoxes) {
                    addSubActivities(ExtractItemFromShulkerBox(item, 1, predicateItem, predicateSlot))
                } else {
                    failedWith(NoItemFoundException(item))
                }
            }
        }
    }

    override fun SafeClientEvent.onSuccess() {
        val currentItem = player.getHeldItem(EnumHand.MAIN_HAND).item

        if (currentItem != item) {
            failedWith(Exception("Failed to move item ${item.registryName} to hotbar (current item: ${currentItem.registryName})"))
        }
    }

    class NoItemFoundException(item: Item) : Exception("No ${item.registryName} found in inventory (shulkers are disabled)")
}