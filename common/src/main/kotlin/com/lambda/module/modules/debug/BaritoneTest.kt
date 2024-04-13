package com.lambda.module.modules.debug

import baritone.api.BaritoneAPI
import baritone.api.pathing.goals.GoalXZ
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object BaritoneTest : Module(
    name = "BaritoneTest",
    description = "Test Baritone",
    tag = ModuleTag.DEBUG
) {
    init {
        listener<TickEvent.Pre> {
            BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.setGoalAndPath(GoalXZ(0, 0))
        }
    }
}