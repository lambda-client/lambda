package com.lambda.client.activity.activities.highlevel

import baritone.api.pathing.goals.GoalNear
import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.interaction.PlaceBlock
import com.lambda.client.activity.activities.inventory.SwapOrMoveToItem
import com.lambda.client.activity.activities.travel.CustomGoal
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.item
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.util.math.BlockPos

class PlaceBlockSafely(
    private val blockPos: BlockPos,
    private val blockState: IBlockState,
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        addSubActivities(
            SwapOrMoveToItem(blockState.block.item),
            CustomGoal(GoalNear(blockPos, 3)),
            PlaceBlock(blockPos, blockState)
        )
    }
}