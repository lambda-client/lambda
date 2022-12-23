package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import net.minecraft.block.state.IBlockState
import net.minecraft.util.math.BlockPos

class BuildBlock(
    private val blockPos: BlockPos,
    private val targetState: IBlockState,
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        when (val currentState = world.getBlockState(blockPos)) {
            targetState -> activityStatus = ActivityStatus.SUCCESS
            else -> {

            }
        }
    }
}