package com.lambda.task.tasks

import com.lambda.task.Task
import com.lambda.task.TaskCha1nBuilder
import com.lambda.task.TaskChainBuilder
import com.lambda.task.buildChain
import com.lambda.task.tasks.LookAtBlock.Companion.lookAtBlock
import com.lambda.threading.runGameBlocking
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos

class InteractBlock(
    val blockPos: BlockPos
) : Task<Unit>() {

    override suspend fun onAction() {
        buildChain {
            lookAtBlock(blockPos)
                .withTimeout(3000L)
                .onSuccess { request ->
                    runGameBlocking {
                        val cast = request.rotation.rayCast(5.0)?.blockResult ?: throw IllegalStateException("Failed to raycast block")
                        interaction.interactBlock(player, Hand.MAIN_HAND, cast)
                    }
                }
        }.execute()
    }

    companion object {
        @TaskCha1nBuilder
        fun TaskChainBuilder.interactWithBlock(blockPos: BlockPos) =
            InteractBlock(blockPos).apply {
                required(this)
            }
    }
}