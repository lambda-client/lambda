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
    init {
        onEnable {
            runSafe {
                buildStructure(StructureTask(generateBlueprint()))
            }
        }

        onDisable {

        }
    }

    private fun SafeClientEvent.generateBlueprint(): HashMap<BlockPos, TaskFactory.BlueprintTask> {
        val blueprint = hashMapOf<BlockPos, TaskFactory.BlueprintTask>()

        val origin = player.flooredPosition.add(Direction.fromEntity(player).directionVec)

        (0..2).forEach {
            blueprint[origin.up(it)] = TaskFactory.BlueprintTask(Blocks.OBSIDIAN)
        }

        return blueprint
    }
}