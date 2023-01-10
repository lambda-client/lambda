package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.LoopWhileActivity
import com.lambda.client.activity.activities.utils.Wait
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.EntityUtils.flooredPosition
import net.minecraft.init.Blocks
import net.minecraft.util.math.BlockPos

class SurroundWithObsidian(
    private val originPos: BlockPos,
    override val loopWhile: SafeClientEvent.() -> Boolean = {
        originPos == player.flooredPosition
    },
    override var currentLoops: Int = 0
) : LoopWhileActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        addSubActivities(
            BuildBlock(originPos.north(), Blocks.SLIME_BLOCK.defaultState),
            BuildBlock(originPos.south(), Blocks.SLIME_BLOCK.defaultState),
            BuildBlock(originPos.east(), Blocks.SLIME_BLOCK.defaultState),
            BuildBlock(originPos.west(), Blocks.SLIME_BLOCK.defaultState),
            Wait(10L)
        )
    }
}