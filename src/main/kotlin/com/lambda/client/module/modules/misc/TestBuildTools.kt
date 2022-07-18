package com.lambda.client.module.modules.misc

import com.lambda.client.buildtools.BuildToolsManager.buildStructure
import com.lambda.client.buildtools.blueprint.StructureTask
import com.lambda.client.buildtools.task.TaskFactory
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.math.Direction
import com.lambda.client.util.threads.runSafe
import net.minecraft.init.Blocks
import net.minecraft.util.math.BlockPos

object TestBuildTools : Module(
    name = "TestBuildTools",
    description = "",
    category = Category.MISC
) {
    private val placeThree = setting("Place Three", false)
    private val breakThree = setting("Break Three", false)

    init {
        placeThree.consumers.add { _, it ->
            if (it) {
                runSafe {
                    buildStructure(StructureTask(generateBlueprint1()))
                }
            }
            false
        }

        breakThree.consumers.add { _, it ->
            if (it) {
                runSafe {
                    buildStructure(StructureTask(generateBlueprint2()))
                }
            }
            false
        }
    }

    private fun SafeClientEvent.generateBlueprint1(): HashMap<BlockPos, TaskFactory.BlueprintTask> {
        val blueprint = hashMapOf<BlockPos, TaskFactory.BlueprintTask>()

        val origin = player.flooredPosition.add(Direction.fromEntity(player).directionVec)

        (0..2).forEach {
            blueprint[origin.up(it)] = TaskFactory.BlueprintTask(Blocks.OBSIDIAN)
        }

        return blueprint
    }

    private fun SafeClientEvent.generateBlueprint2(): HashMap<BlockPos, TaskFactory.BlueprintTask> {
        val blueprint = hashMapOf<BlockPos, TaskFactory.BlueprintTask>()

        val origin = player.flooredPosition.add(Direction.fromEntity(player).directionVec)

        (0..2).forEach {
            blueprint[origin.up(it)] = TaskFactory.BlueprintTask(Blocks.AIR)
        }

        return blueprint
    }
}