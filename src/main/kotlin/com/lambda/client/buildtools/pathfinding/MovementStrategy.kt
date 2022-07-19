package com.lambda.client.buildtools.pathfinding

import baritone.api.process.PathingCommand
import com.lambda.client.buildtools.task.BuildTask
import com.lambda.client.event.SafeClientEvent
import net.minecraft.util.math.BlockPos

interface MovementStrategy {
    fun SafeClientEvent.generatePathingCommand(buildTask: BuildTask): PathingCommand
    fun onLostControl()
}