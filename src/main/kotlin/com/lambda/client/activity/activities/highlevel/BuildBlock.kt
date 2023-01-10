package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.interaction.BreakBlock
import com.lambda.client.activity.activities.interaction.PlaceBlock
import com.lambda.client.activity.activities.inventory.SwapToBestTool
import com.lambda.client.activity.activities.travel.BreakGoal
import com.lambda.client.activity.activities.types.AttemptActivity
import com.lambda.client.activity.activities.types.RenderAABBActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.world.isReplaceable
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.util.math.BlockPos

class BuildBlock(
    private val blockPos: BlockPos,
    private val targetState: IBlockState,
    override val maxAttempts: Int = 3,
    override var usedAttempts: Int = 0,
    override val toRender: MutableSet<RenderAABBActivity.Companion.RenderAABBCompound> = mutableSetOf()
) : AttemptActivity, RenderAABBActivity, Activity() {
    init {
        runSafe {
            val currentState = world.getBlockState(blockPos)

            when {
                /* is in desired state */
                currentState.block == targetState.block -> success()
                /* block needs to be placed */
                targetState.block != Blocks.AIR && currentState.isReplaceable -> {
                    toRender.add(RenderAABBActivity.Companion.RenderBlockPos(
                        blockPos,
                        ColorHolder(35, 188, 254)
                    ))
                }
                /* only option left is breaking the block */
                else -> {
                    toRender.add(RenderAABBActivity.Companion.RenderBlockPos(
                        blockPos,
                        ColorHolder(222, 0, 0)
                    ))
                }
            }
        }
    }

    override fun SafeClientEvent.onInitialize() {
        val currentState = world.getBlockState(blockPos)

        when {
            /* is in desired state */
            currentState.block == targetState.block -> success()
            /* block needs to be placed */
            targetState.block != Blocks.AIR && currentState.isReplaceable -> {
                addSubActivities(
                    PlaceBlock(blockPos, targetState, doPending = true)
                )
            }
            /* only option left is breaking the block */
            else -> {
                addSubActivities(
                    SwapToBestTool(blockPos),
                    BreakGoal(blockPos),
                    BreakBlock(blockPos),
                    PlaceBlock(blockPos, targetState)
                )
            }
        }
    }
}