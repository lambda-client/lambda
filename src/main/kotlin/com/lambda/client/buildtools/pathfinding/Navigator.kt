package com.lambda.client.buildtools.pathfinding

import baritone.api.process.PathingCommand
import baritone.api.process.PathingCommandType
import com.lambda.client.buildtools.pathfinding.strategies.CenterStrategy
import com.lambda.client.buildtools.task.TaskProcessor
import com.lambda.client.event.SafeClientEvent
import net.minecraft.util.math.BlockPos

object Navigator {
    var origin: BlockPos = BlockPos.ORIGIN
    var movementStrategy: MovementStrategy = CenterStrategy
    var currentPathingCommand = PathingCommand(null, PathingCommandType.REQUEST_PAUSE)

    inline fun <reified T : MovementStrategy> changeStrategy() {
        movementStrategy = T::class.java.newInstance()
    }

    fun reset() {
        movementStrategy = CenterStrategy
    }

    private fun SafeClientEvent.getPathingCommand(): PathingCommand {
        with(movementStrategy) {
            TaskProcessor.currentBuildTask?.let {
                return generatePathingCommand(it.blockPos)
            }

            return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
        }
    }

    fun processInfo(): String {
        return "BuildTools: ${currentPathingCommand.commandType.name}@${currentPathingCommand.goal ?: origin} ${TaskProcessor.currentBuildTask ?: ""}"
    }
}