package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.interaction.BreakBlock
import com.lambda.client.activity.activities.interaction.PlaceBlock
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.world.isReplaceable
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.util.math.BlockPos

class BuildBlock(
    private val blockPos: BlockPos,
    private val targetState: IBlockState,
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        val currentState = world.getBlockState(blockPos)

        when {
            /* is in desired state */
            currentState == targetState -> success()
            /* is blocked by entity */
            !world.checkNoEntityCollision(currentState.getSelectedBoundingBox(world, blockPos), null) -> {
                failedWith(EntityCollisionException())
            }
            /* block needs to be placed */
            targetState.block != Blocks.AIR && currentState.isReplaceable -> {
                addSubActivities(
                    PlaceBlock(blockPos, targetState)
                )
            }
            /* only option left is breaking the block */
            else -> {
                addSubActivities(
                    BreakBlock(blockPos),
                    PlaceBlock(blockPos, targetState)
                )
            }
        }
    }

    class EntityCollisionException : Exception("entity collision")
}