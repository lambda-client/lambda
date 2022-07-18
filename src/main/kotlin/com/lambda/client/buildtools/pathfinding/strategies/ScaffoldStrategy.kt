package com.lambda.client.buildtools.pathfinding.strategies

import baritone.api.process.PathingCommand
import baritone.api.process.PathingCommandType
import com.lambda.client.buildtools.pathfinding.MovementStrategy
import com.lambda.client.event.SafeClientEvent
import net.minecraft.util.math.BlockPos

object ScaffoldStrategy : MovementStrategy {
    override fun SafeClientEvent.generatePathingCommand(blockPos: BlockPos): PathingCommand {
        return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
    }

    override fun onLostControl() {

    }
}