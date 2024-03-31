package com.lambda.util

import baritone.api.BaritoneAPI
import baritone.api.IBaritone
import baritone.api.Settings
import baritone.api.pathing.goals.Goal

object BaritoneUtils {
    private val baritone = BaritoneAPI.getProvider()
    val settings: Settings = BaritoneAPI.getSettings()
    @JvmStatic val primary: IBaritone = baritone.primaryBaritone

    val isPathing: Boolean
        get() = primary.pathingBehavior.isPathing

    val isActive: Boolean
        get() = primary.customGoalProcess.isActive || primary.pathingBehavior.isPathing || primary.pathingControlManager.mostRecentInControl().orElse(null)?.isActive == true

    fun setGoalAndPath(goal: Goal) = primary.customGoalProcess.setGoalAndPath(goal)

    fun cancel() = primary.pathingBehavior.cancelEverything()
}
