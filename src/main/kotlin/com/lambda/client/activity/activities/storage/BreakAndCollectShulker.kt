package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.interaction.BreakBlock
import com.lambda.client.activity.activities.inventory.SwapToBestTool
import com.lambda.client.event.SafeClientEvent
import net.minecraft.util.math.BlockPos

class BreakAndCollectShulker(
    private val blockPos: BlockPos
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        addSubActivities(
            SwapToBestTool(blockPos),
            BreakBlock(
                blockPos,
                pickUpDrop = true
            )
        )
    }
}