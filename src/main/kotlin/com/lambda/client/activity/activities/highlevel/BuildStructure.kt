package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.interaction.BreakBlockRaw
import com.lambda.client.event.SafeClientEvent
import net.minecraft.block.state.IBlockState
import net.minecraft.util.math.BlockPos

class BuildStructure(
    private val structure: Map<BlockPos, IBlockState>
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        structure.forEach { (pos, state) ->
            addSubActivities(
                BuildBlock(pos, state).also {
                    executeOnFailure = { if (it is BreakBlockRaw.ExceptionNoSurfaceExposed) refresh() }
                }
            )
        }
    }
}