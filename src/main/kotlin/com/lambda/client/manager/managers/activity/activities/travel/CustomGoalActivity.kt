package com.lambda.client.manager.managers.activity.activities.travel

import baritone.api.pathing.goals.Goal
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.TimeoutActivity
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent

class CustomGoalActivity(
    private val goal: Goal,
    override val timeout: Long = 100000L,
    override var creationTime: Long = 0L
) : TimeoutActivity, Activity() {
    init {
        safeListener<TickEvent.ClientTickEvent> { event ->
            if (event.phase != TickEvent.Phase.START) return@safeListener

            BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(goal)

            if (goal.isInGoal(player.flooredPosition)) activityStatus = ActivityStatus.SUCCESS
        }
    }
}