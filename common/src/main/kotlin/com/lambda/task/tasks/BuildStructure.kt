package com.lambda.task.tasks

import com.lambda.interaction.construction.Blueprint
import com.lambda.interaction.construction.Blueprint.Companion.from
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.task.Task
import com.lambda.task.TaskCha1nBuilder
import com.lambda.task.TaskChainBuilder
import net.minecraft.util.math.BlockPos

class BuildStructure(
    private val blueprint: Blueprint,
    private val collectDrops: Boolean = false,
    private val skipWeakBlocks: Boolean = false,
    private val pathing: Boolean = true,
) : Task<Unit>() {
    override suspend fun onAction() {
        blueprint.structureMap
    }

    companion object {
        @TaskCha1nBuilder
        fun TaskChainBuilder.buildStructure(
            blueprint: Blueprint,
            collectDrops: Boolean = false,
            skipWeakBlocks: Boolean = false,
            pathing: Boolean = true,
        ) = BuildStructure(
                blueprint,
                collectDrops,
                skipWeakBlocks,
                pathing
            ).apply {
                required(this)
            }

        @TaskCha1nBuilder
        fun TaskChainBuilder.breakAndCollectBlock(
            blockPos: BlockPos
        ) = BuildStructure(blockPos.from(TargetState.Air), collectDrops = true).apply {
            required(this)
        }
    }
}