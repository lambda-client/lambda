package com.lambda.client.activity.activities.interaction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.item
import net.minecraft.block.state.IBlockState
import net.minecraft.util.math.BlockPos

class PlaceBlockWithItem(
    private val blockPos: BlockPos,
    private val targetState: IBlockState
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        addSubActivities(
            AcquireItemInActiveHand(targetState.block.item),
            PlaceBlock(blockPos, targetState)
        )
    }
}