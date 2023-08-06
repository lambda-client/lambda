package com.lambda.client.activity.activities.inventory.core

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.hotbarSlots
import com.lambda.client.util.items.toHotbarSlotOrNull
import net.minecraft.inventory.Slot

class SwapOrSwitchToSlot(
    private val slotFrom: Slot,
    private val hotbarFilter: (Slot) -> Boolean = { true }
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        slotFrom.toHotbarSlotOrNull()?.let { hotbarSlot ->
            addSubActivities(SwitchToHotbarSlot(hotbarSlot))
        } ?: run {
            val slotTo = player.hotbarSlots.firstOrNull { hotbarFilter(it.slot) } ?: return

            addSubActivities(
                SwapWithSlot(slotFrom, slotTo.hotbarSlot),
                SwitchToHotbarSlot(slotTo)
            )
        }
    }
}