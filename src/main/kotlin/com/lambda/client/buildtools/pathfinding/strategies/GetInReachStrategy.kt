package com.lambda.client.buildtools.pathfinding.strategies

import baritone.api.pathing.goals.GoalNear
import baritone.api.process.PathingCommand
import baritone.api.process.PathingCommandType
import com.lambda.client.buildtools.pathfinding.MovementStrategy
import com.lambda.client.buildtools.task.BuildTask
import com.lambda.client.commons.extension.floorToInt
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.client.BuildTools.maxReach
import net.minecraft.util.math.BlockPos

object GetInReachStrategy : MovementStrategy {
    override fun SafeClientEvent.generatePathingCommand(buildTask: BuildTask): PathingCommand {
        return PathingCommand(GoalNear(buildTask.blockPos, maxReach.floorToInt()), PathingCommandType.SET_GOAL_AND_PATH)
    }

    override fun onLostControl() {
        /* ignored */
    }
}