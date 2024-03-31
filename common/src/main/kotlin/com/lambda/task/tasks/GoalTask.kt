package com.lambda.task.tasks

import baritone.api.BaritoneAPI
import baritone.api.pathing.goals.Goal
import baritone.api.pathing.goals.GoalXZ
import com.lambda.event.EventFlow.awaitEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.task.Task
import com.lambda.task.TaskCha1nBuilder
import com.lambda.task.TaskChainBuilder
import net.minecraft.util.math.BlockPos

class GoalTask(
    private val goal: Goal
) : Task<Unit>() {
    override suspend fun onAction() {
        BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.setGoalAndPath(goal)

        awaitEvent<TickEvent.Post> {
            BaritoneAPI.getProvider().primaryBaritone.pathingBehavior.isPathing
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