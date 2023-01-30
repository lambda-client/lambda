package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.interaction.PlaceBlock
import com.lambda.client.activity.activities.types.AttemptActivity
import com.lambda.client.activity.activities.types.AttemptActivity.Companion.checkAttempt
import com.lambda.client.activity.activities.utils.getContainerPos
import com.lambda.client.event.SafeClientEvent
import net.minecraft.block.state.IBlockState
import net.minecraft.util.math.BlockPos

class PlaceContainer(
    private val targetState: IBlockState,
    override val maxAttempts: Int = 5,
    override var usedAttempts: Int = 0
) : AttemptActivity, Activity() {
    var containerPos: BlockPos = BlockPos.ORIGIN

    override fun SafeClientEvent.onInitialize() {
        if (containerPos == BlockPos.ORIGIN) {
            containerPos = getContainerPos(targetState) ?: run {
                failedWith(NoContainerPlacePositionFoundException())
                return
            }
        }

        addSubActivities(PlaceBlock(
            containerPos,
            targetState,
            ignoreDirection = true,
            ignoreProperties = true
        ))
    }

    override fun SafeClientEvent.onChildFailure(childActivities: ArrayDeque<Activity>, childException: Exception): Boolean {
        if (childActivities.firstOrNull() !is PlaceBlock) return false

        checkAttempt(this@PlaceContainer, childException)
        containerPos = BlockPos.ORIGIN
        return true
    }

    class NoContainerPlacePositionFoundException : Exception("No position to place a container was found")
}