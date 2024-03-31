package com.lambda.task.tasks

import baritone.api.pathing.goals.Goal
import baritone.api.pathing.goals.GoalXZ
import com.lambda.event.EventFlow.awaitEvent
import com.lambda.event.events.TickEvent
import com.lambda.task.Task
import com.lambda.task.TaskCha1nBuilder
import com.lambda.task.TaskChainBuilder
import com.lambda.util.BaritoneUtils
import net.minecraft.util.math.BlockPos

class GoalTask(
    private val goal: Goal
) : Task<Unit>() {
    override suspend fun onAction() {
        BaritoneUtils.setGoalAndPath(goal)

        awaitEvent<TickEvent.Post> {
            BaritoneUtils.isPathing
        }
    }

    companion object {
        @TaskCha1nBuilder
        fun TaskChainBuilder.moveIntoEntityRange(blockPos: BlockPos) =
            GoalTask(GoalXZ(blockPos.x, blockPos.z)).apply {
                required(this)
            }
    }
}