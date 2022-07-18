package com.lambda.client.buildtools.pathfinding

import baritone.api.process.PathingCommand
import com.lambda.client.event.SafeClientEvent
import net.minecraft.util.math.BlockPos

interface MovementStrategy {
    fun SafeClientEvent.generatePathingCommand(blockPos: BlockPos): PathingCommand
    fun onLostControl()
}