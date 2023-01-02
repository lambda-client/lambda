package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.interaction.OpenContainer
import com.lambda.client.activity.activities.interaction.PlaceBlock
import com.lambda.client.activity.activities.inventory.SwapOrSwitchToSlot
import com.lambda.client.activity.activities.utils.getContainerPos
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.block
import net.minecraft.inventory.Slot
import net.minecraft.util.math.BlockPos

class OpenContainerInSlot(
    private val slot: Slot
) : Activity() {
    var containerPos: BlockPos = BlockPos.ORIGIN

    override fun SafeClientEvent.onInitialize() {
        containerPos = getContainerPos() ?: run {
            failedWith(NoContainerPlacePositionFoundException())
            return
        }

        addSubActivities(
            SwapOrSwitchToSlot(slot),
            PlaceBlock(containerPos, slot.stack.item.block.defaultState, swapToItem = false),
            OpenContainer(containerPos)
        )
    }

    class NoContainerPlacePositionFoundException : Exception("No position to place a container was found")
}