package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.interaction.OpenContainer
import com.lambda.client.activity.activities.interaction.PlaceBlock
import com.lambda.client.activity.activities.interaction.PlaceBlockRaw
import com.lambda.client.activity.activities.inventory.SwapOrSwitchToSlot
import com.lambda.client.activity.activities.types.AttemptActivity
import com.lambda.client.activity.activities.utils.Wait
import com.lambda.client.activity.activities.utils.getContainerPos
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.block
import com.lambda.client.util.text.MessageSendHelper
import net.minecraft.inventory.Slot
import net.minecraft.util.math.BlockPos

class OpenContainerInSlot(
    private val slot: Slot,
    override val maxAttempts: Int = 5,
    override var usedAttempts: Int = 0
) : AttemptActivity, Activity() {
    var containerPos: BlockPos = BlockPos.ORIGIN

    override fun SafeClientEvent.onInitialize() {
        containerPos = getContainerPos() ?: run {
            failedWith(NoContainerPlacePositionFoundException())
            return
        }

        addSubActivities(
            SwapOrSwitchToSlot(slot),
            PlaceBlockRaw(containerPos, slot.stack.item.block.defaultState).also {
                executeOnFailure = {
                    if (it is PlaceBlockRaw.NoNeighbourException) {
                        usedAttempts++
                        initialize()
                        MessageSendHelper.sendErrorMessage("No neighbour found, retrying...")
                        true
                    } else false
                }
            },
            OpenContainer(containerPos)
        )
    }

    class NoContainerPlacePositionFoundException : Exception("No position to place a container was found")
}