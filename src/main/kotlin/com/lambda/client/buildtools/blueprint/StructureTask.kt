package com.lambda.client.buildtools.blueprint

import com.lambda.client.buildtools.blueprint.strategies.DontMoveStrategy
import com.lambda.client.buildtools.task.TaskFactory
import com.lambda.client.buildtools.task.sequence.TaskSequenceStrategy
import com.lambda.client.buildtools.task.sequence.strategies.OriginStrategy
import net.minecraft.util.math.BlockPos

data class StructureTask(
    var blueprint: HashMap<BlockPos, TaskFactory.BlueprintTask>,
    val blueprintStrategy: BlueprintStrategy = DontMoveStrategy,
    val taskSequenceStrategy: TaskSequenceStrategy = OriginStrategy
) {
    var inProgress = false
    var cancel = false
}