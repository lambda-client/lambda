package com.lambda.client.activity.activities.travel

import baritone.api.pathing.goals.Goal
import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.TimeoutActivity
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent

class CustomGoal(
    private val goal: Goal,
    override val timeout: Long = 100000L
) : TimeoutActivity, Activity() {
    init {
        safeListener<TickEvent.ClientTickEvent> { event ->
            if (event.phase != TickEvent.Phase.START) return@safeListener

            BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(goal)

            if (goal.isInGoal(player.flooredPosition)) activityStatus = ActivityStatus.SUCCESS
        }
    }
}