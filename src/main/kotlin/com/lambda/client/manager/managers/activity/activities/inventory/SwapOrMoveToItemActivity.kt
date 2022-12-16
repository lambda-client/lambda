package com.lambda.client.manager.managers.activity.activities.inventory

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.InstantActivity
import com.lambda.client.util.items.*
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class SwapOrMoveToItemActivity(
    private val item: Item,
    private val predicateItem: (ItemStack) -> Boolean = { true },
    private val predicateSlot: (ItemStack) -> Boolean = { true }
) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        player.hotbarSlots.firstOrNull { hotbarSlot ->
            hotbarSlot.stack.item.id == item.id && predicateItem(hotbarSlot.stack)
        }?.let { hotbarSlot ->
            subActivities.add(SwitchToHotbarSlotActivity(hotbarSlot.hotbarSlot))
        } ?: run {
            player.storageSlots.firstOrNull { slot ->
                slot.stack.item.id == item.id && predicateItem(slot.stack)
            }?.let { slotFrom ->
                val hotbarSlots = player.hotbarSlots
                val slotTo = hotbarSlots.firstEmpty()
                    ?: hotbarSlots.firstOrNull { predicateSlot(it.stack) } ?: return

                subActivities.add(SwapWithSlotActivity(slotFrom, slotTo))
                subActivities.add(SwitchToHotbarSlotActivity(slotTo.hotbarSlot))
            }
        }
    }
}