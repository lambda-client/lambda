package com.lambda.client.module.modules.misc

import com.lambda.client.buildtools.BuildToolsManager.buildStructure
import com.lambda.client.buildtools.blueprint.StructureTask
import com.lambda.client.buildtools.blueprint.strategies.MoveXStrategy
import com.lambda.client.buildtools.task.RestockHandler.createRestockStructure
import com.lambda.client.buildtools.task.RestockHandler.restockItem
import com.lambda.client.buildtools.task.TaskFactory
import com.lambda.client.buildtools.task.sequence.strategies.OriginStrategy
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.items.item
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
    private val line = setting("line", false)
    private val container = setting("container", false)
    private val cancel = setting("Cancel", false)

    lateinit var st: StructureTask

    init {
        placeThree.consumers.add { _, it ->
            if (it) {
                runSafe {
                    st = StructureTask(generateBlueprint1())

                    buildStructure(st)
                }
            }
            false
        }

        breakThree.consumers.add { _, it ->
            if (it) {
                runSafe {
                    st = StructureTask(generateBlueprint2())

                    buildStructure(st)
                }
            }
            false
        }

        line.consumers.add { _, it ->
            if (it) {
                runSafe {
                    st = StructureTask(generateBlueprint3(),
                        blueprintStrategy = MoveXStrategy(player.flooredPosition, Direction.fromEntity(player), 1, 0, hasStartPadding = true),
                        taskSequenceStrategy = OriginStrategy
                    )

                    buildStructure(st)
                }
            }
            false
        }

        container.consumers.add { _, it ->
            if (it) {
                runSafe {
                    createRestockStructure(Blocks.NETHERRACK.item)
                }
            }
            false
        }

        cancel.consumers.add { _, it ->
            if (it) {
                runSafe {
                    st.cancel = true
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

    private fun SafeClientEvent.generateBlueprint3(): HashMap<BlockPos, TaskFactory.BlueprintTask> {
        val blueprint = hashMapOf<BlockPos, TaskFactory.BlueprintTask>()

        blueprint[player.flooredPosition.add(Direction.fromEntity(player).directionVec)] = TaskFactory.BlueprintTask(Blocks.NETHERRACK)

        return blueprint
    }
}