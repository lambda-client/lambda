package com.lambda.client.buildtools.blueprint

import com.lambda.client.buildtools.task.TaskFactory
import net.minecraft.util.math.BlockPos

interface BlueprintStrategy {
    fun getNext(blueprint: HashMap<BlockPos, TaskFactory.BlueprintTask>): HashMap<BlockPos, TaskFactory.BlueprintTask>
    fun getFinishMessage(): String
    fun isDone(): Boolean
}