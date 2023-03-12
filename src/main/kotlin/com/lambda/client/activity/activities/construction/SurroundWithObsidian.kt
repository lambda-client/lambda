package com.lambda.client.activity.activities.construction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.construction.core.BuildStructure
import com.lambda.client.activity.types.LoopWhileActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.EntityUtils.flooredPosition
import net.minecraft.block.state.IBlockState
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
        val material = Blocks.OBSIDIAN.defaultState

        val structure = mutableMapOf<BlockPos, IBlockState>(
            originPos.north() to material,
            originPos.south() to material,
            originPos.east() to material,
            originPos.west() to material,
        )

        addSubActivities(
            BuildStructure(structure)
        )
    }
}