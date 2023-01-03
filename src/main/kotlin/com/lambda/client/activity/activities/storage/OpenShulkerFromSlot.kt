package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.interaction.OpenContainer
import com.lambda.client.activity.activities.inventory.SwapOrSwitchToSlot
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.block
import net.minecraft.inventory.Slot
import net.minecraft.util.math.BlockPos

class OpenShulkerFromSlot(
    private val slot: Slot
) : Activity() {
    var containerPos: BlockPos = BlockPos.ORIGIN

    override fun SafeClientEvent.onInitialize() {
        val targetState = slot.stack.item.block.defaultState

        addSubActivities(
            SwapOrSwitchToSlot(slot),
            PlaceContainer(targetState)
        )
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        if (childActivity !is PlaceContainer) return

        containerPos = childActivity.containerPos

        addSubActivities(
            OpenContainer(containerPos)
        )
    }
}