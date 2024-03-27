package com.lambda.module

import baritone.api.BaritoneAPI
import baritone.api.pathing.goals.GoalXZ
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener

object BaritoneTest : Module(
    name = "BaritoneTest",
    description = "Test Baritone"
) {
    init {
        listener<TickEvent.Pre> {
            BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.setGoalAndPath(GoalXZ(0, 0))
        }
    }
}