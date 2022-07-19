package com.lambda.client.buildtools.task

import com.lambda.client.buildtools.blueprint.StructureTask
import com.lambda.client.buildtools.blueprint.strategies.MoveXStrategy
import com.lambda.client.buildtools.task.TaskProcessor.addTask
import com.lambda.client.buildtools.task.build.BreakTask
import com.lambda.client.buildtools.task.build.DoneTask
import com.lambda.client.buildtools.task.build.PlaceTask
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.client.BuildTools.ignoreBlocks
import com.lambda.client.util.world.isReplaceable
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos

object TaskFactory {
    private var currentBlueprint = hashMapOf<BlockPos, BlueprintTask>()

    fun SafeClientEvent.populateTasks(structureTask: StructureTask) {
        currentBlueprint = structureTask.blueprint

        /* generate tasks based on the blueprint */
        currentBlueprint.forEach { (pos, blueprintTask) ->
            generateTask(pos, blueprintTask, structureTask)
        }
    }

    private fun SafeClientEvent.generateTask(blockPos: BlockPos, blueprintTask: BlueprintTask, structureTask: StructureTask) {
        val currentState = world.getBlockState(blockPos)

        when {
            /* start padding */
            structureTask.blueprintStrategy is MoveXStrategy
                && structureTask.blueprintStrategy.hasStartPadding
                && structureTask.blueprintStrategy.isInPadding(blockPos) -> { /* ignore task */ }

            /* prevent overriding container tasks */
            TaskProcessor.tasks[blockPos]?.isContainerTask == true
                && TaskProcessor.tasks[blockPos] !is DoneTask -> { /* ignore task */ }

            /* ignored blocks */
            shouldBeIgnored(blockPos, currentState) -> {
                addTask(DoneTask(blockPos, currentState.block))
            }

            /* is in desired state */
            currentState.block == blueprintTask.targetBlock -> {
                addTask(DoneTask(blockPos, currentState.block))
            }

            /* block needs to be placed */
            blueprintTask.targetBlock != Blocks.AIR && currentState.isReplaceable -> {
                /* support not needed */
                if (blueprintTask.isSupport && TaskProcessor.tasks[blockPos.up()] is DoneTask) {
                    addTask(DoneTask(blockPos, currentState.block))
                    return
                }

                /* is blocked by entity */
                if (!world.checkNoEntityCollision(AxisAlignedBB(blockPos), null)) {
                    addTask(DoneTask(blockPos, currentState.block))
                    return
                }

                addTask(PlaceTask(blockPos, blueprintTask.targetBlock, isFillerTask = blueprintTask.isFiller, isSupportTask = blueprintTask.isSupport))
            }

            /* only option left is breaking the block */
            else -> {
                /* Is already filled */
                if (blueprintTask.isFiller) {
                    addTask(DoneTask(blockPos, currentState.block))
                    return
                }

                addTask(BreakTask(blockPos, blueprintTask.targetBlock, isSupportTask = blueprintTask.isSupport))
            }
        }
    }

    private fun shouldBeIgnored(blockPos: BlockPos, currentState: IBlockState) =
        ignoreBlocks.contains(currentState.block.registryName.toString())
            && !isInsideBlueprintBuilding(blockPos)

    fun isInsideBlueprintBuilding(blockPos: BlockPos): Boolean {
        return currentBlueprint[blockPos]?.let {
            it.targetBlock != Blocks.AIR
        } ?: false
    }

    data class BlueprintTask(val targetBlock: Block, val isFiller: Boolean = false, val isSupport: Boolean = false)
}