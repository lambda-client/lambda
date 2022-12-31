package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.interaction.OpenContainer
import com.lambda.client.activity.activities.interaction.PlaceBlock
import com.lambda.client.activity.activities.inventory.SwapOrSwitchToSlot
import com.lambda.client.activity.activities.utils.getContainerPos
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.block
import net.minecraft.inventory.Slot

class OpenContainerInSlot(
    private val slot: Slot
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        getContainerPos()?.let { containerPos ->
            addSubActivities(
                SwapOrSwitchToSlot(slot),
                PlaceBlock(containerPos, slot.stack.item.block.defaultState, swapToItem = false),
                OpenContainer(containerPos)
            )
        }
    }
}