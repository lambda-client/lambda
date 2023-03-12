package com.lambda.client.activity.activities.travel

import baritone.api.pathing.goals.Goal
import com.lambda.client.activity.Activity
import com.lambda.client.activity.types.TimeoutActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent

class CustomGoal(
    private val goal: Goal,
    override val timeout: Long = 100000L
) : TimeoutActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        if (!goal.isInGoal(player.flooredPosition)) return

        success()
    }

    init {
        safeListener<TickEvent.ClientTickEvent> { event ->
            if (event.phase != TickEvent.Phase.START) return@safeListener

            BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(goal)

            if (goal.isInGoal(player.flooredPosition)) success()
        }
    }
}