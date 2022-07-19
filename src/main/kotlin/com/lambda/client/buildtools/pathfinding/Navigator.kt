package com.lambda.client.buildtools.pathfinding

import baritone.api.process.PathingCommand
import baritone.api.process.PathingCommandType
import com.lambda.client.LambdaMod
import com.lambda.client.buildtools.pathfinding.strategies.CenterStrategy
import com.lambda.client.buildtools.pathfinding.strategies.GetInReachStrategy
import com.lambda.client.buildtools.pathfinding.strategies.PickupStrategy
import com.lambda.client.buildtools.pathfinding.strategies.ScaffoldStrategy
import com.lambda.client.buildtools.task.TaskProcessor
import com.lambda.client.event.SafeClientEvent
import net.minecraft.util.math.BlockPos

object Navigator {
    var origin: BlockPos = BlockPos.ORIGIN
    var movementStrategy: MovementStrategy = GetInReachStrategy
    var currentPathingCommand = PathingCommand(null, PathingCommandType.REQUEST_PAUSE)

    inline fun <reified T : MovementStrategy> changeStrategy() {
        movementStrategy = when (T::class) {
            CenterStrategy::class -> CenterStrategy
            GetInReachStrategy::class -> GetInReachStrategy
            PickupStrategy::class -> PickupStrategy
            else -> ScaffoldStrategy
        }
    }

    fun reset() {
        movementStrategy = GetInReachStrategy
    }

    fun SafeClientEvent.updatePathingCommand() {
        with(movementStrategy) {
            TaskProcessor.currentBuildTask?.let {
                currentPathingCommand = generatePathingCommand(it)
                return
            }
            currentPathingCommand = PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
        }
    }

    fun processInfo(): String {
        return "BuildTools: ${currentPathingCommand.commandType.name}@${currentPathingCommand.goal ?: origin} ${TaskProcessor.currentBuildTask ?: ""}"
    }
}