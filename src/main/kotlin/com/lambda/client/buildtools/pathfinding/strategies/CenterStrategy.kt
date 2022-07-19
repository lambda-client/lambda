package com.lambda.client.buildtools.pathfinding.strategies

import baritone.api.pathing.goals.GoalNear
import baritone.api.process.PathingCommand
import baritone.api.process.PathingCommandType
import com.lambda.client.buildtools.pathfinding.MovementStrategy
import com.lambda.client.buildtools.task.BuildTask
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.client.BuildTools.moveSpeed
import com.lambda.client.util.math.VectorUtils.toVec3dCenter
import net.minecraft.util.math.BlockPos

object CenterStrategy : MovementStrategy {
    override fun SafeClientEvent.generatePathingCommand(buildTask: BuildTask): PathingCommand {
        val target = buildTask.blockPos.up().toVec3dCenter()
        return if (player.positionVector.distanceTo(target) < 1) {
            player.motionX = (target.x - player.posX).coerceIn(-moveSpeed, moveSpeed)
            player.motionZ = (target.z - player.posZ).coerceIn(-moveSpeed, moveSpeed)
            PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
        } else {
            PathingCommand(GoalNear(buildTask.blockPos, 0), PathingCommandType.SET_GOAL_AND_PATH)
        }
    }

    override fun onLostControl() {
        // Ignore
    }
}