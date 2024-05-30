package com.lambda.task.tasks

import baritone.api.pathing.goals.Goal
import baritone.api.pathing.goals.GoalBlock
import baritone.api.pathing.goals.GoalNear
import baritone.api.pathing.goals.GoalXZ
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.task.Task
import com.lambda.util.BaritoneUtils
import net.minecraft.util.math.BlockPos

// ToDo: Custom heuristic goals
class GoalTask(
    private val goal: Goal,
    private val check: SafeContext.() -> Boolean = { false }
) : Task<Unit>() {

    override fun SafeContext.onStart() {
        BaritoneUtils.setGoalAndPath(goal)
    }

    init {
        listener<TickEvent.Post> {
            if (!BaritoneUtils.isActive || check()) {
                success(Unit)
            }
        }
    }

    companion object {
        @Ta5kBuilder
        fun moveToBlock(blockPos: BlockPos) =
            GoalTask(GoalBlock(blockPos))

        @Ta5kBuilder
        fun moveToXY(blockPos: BlockPos) =
            GoalTask(GoalXZ(blockPos.x, blockPos.z))

        @Ta5kBuilder
        fun moveUntilLoaded(blockPos: BlockPos) =
            GoalTask(GoalBlock(blockPos)) {
                world.isPosLoaded(blockPos.x, blockPos.z)
            }

        @Ta5kBuilder
        fun moveIntoEntityRange(blockPos: BlockPos, range: Int = 3) =
            GoalTask(GoalNear(blockPos, range))
    }
}