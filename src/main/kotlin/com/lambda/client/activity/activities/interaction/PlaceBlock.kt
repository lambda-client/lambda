package com.lambda.client.activity.activities.interaction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.item
import net.minecraft.block.state.IBlockState
import net.minecraft.util.math.BlockPos

class PlaceBlock(
    private val blockPos: BlockPos,
    private val targetState: IBlockState,
    private val playSound: Boolean = true,
    private val swapToItem: Boolean = true,
    private val getInReach: Boolean = true,
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        if (swapToItem) addSubActivities(AcquireItemInActiveHand(targetState.block.item))
//        if (getInReach) addSubActivities(CustomGoal(GoalNear(blockPos, 4)))
        addSubActivities(PlaceBlockRaw(blockPos, targetState, playSound))
    }
}