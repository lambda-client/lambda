package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.EntityUtils.flooredPosition
import net.minecraft.init.Blocks

class SurroundWithObsidian : Activity() {
    override fun SafeClientEvent.onInitialize() {
        player.flooredPosition.let {
            addSubActivities(
                BuildBlock(it.north(), Blocks.SLIME_BLOCK.defaultState),
                BuildBlock(it.south(), Blocks.SLIME_BLOCK.defaultState),
                BuildBlock(it.east(), Blocks.SLIME_BLOCK.defaultState),
                BuildBlock(it.west(), Blocks.SLIME_BLOCK.defaultState)
            )
        }
    }
}