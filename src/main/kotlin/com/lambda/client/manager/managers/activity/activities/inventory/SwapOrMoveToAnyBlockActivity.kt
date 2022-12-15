package com.lambda.client.manager.managers.activity.activities.inventory

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.types.InstantActivity
import com.lambda.client.util.items.*
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack

class SwapOrMoveToAnyBlockActivity(
    private val predicateItem: (ItemStack) -> Boolean = { true },
    private val predicateSlot: (ItemStack) -> Boolean = { true }
) : InstantActivity() {
    override fun SafeClientEvent.onInitialize() {
        player.hotbarSlots.firstItem<ItemBlock, HotbarSlot>(predicateItem)?.let {
            subActivities.add(SwitchToHotbarSlotActivity(it.hotbarSlot))
        } ?: run {
            player.inventorySlots.firstItem<ItemBlock, Slot>(predicateItem)?.let {
                val hotbarSlot = player.hotbarSlots.firstEmpty() ?: return@run
                subActivities.add(SwapWithSlotActivity(it, hotbarSlot))
                subActivities.add(SwitchToHotbarSlotActivity(hotbarSlot.hotbarSlot))
            }
        }
    }
}