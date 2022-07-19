package com.lambda.client.buildtools.blueprint.strategies

import com.lambda.client.buildtools.blueprint.BlueprintStrategy
import com.lambda.client.buildtools.task.TaskFactory
import net.minecraft.util.math.BlockPos

object DontMoveStrategy : BlueprintStrategy {
    override fun getNext(blueprint: HashMap<BlockPos, TaskFactory.BlueprintTask>) = blueprint
    override fun getFinishMessage() = ""

    override fun isDone() = true
}