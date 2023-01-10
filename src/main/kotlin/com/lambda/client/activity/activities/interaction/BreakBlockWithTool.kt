package com.lambda.client.activity.activities.interaction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.SwapToBestTool
import com.lambda.client.event.SafeClientEvent
import net.minecraft.util.math.BlockPos

class BreakBlockWithTool(
    private val blockPos: BlockPos,
    private val miningSpeedFactor: Float = 1.0f,
    private val collectDrops: Boolean = false,
    private val minCollectAmount: Int = 1
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        addSubActivities(
            SwapToBestTool(blockPos),
            BreakBlock(blockPos, miningSpeedFactor, collectDrops, minCollectAmount)
        )
    }
}