package com.lambda.util

import baritone.api.BaritoneAPI
import baritone.api.IBaritone
import baritone.api.Settings

object BaritoneUtils {
    private val baritone = BaritoneAPI.getProvider()
    val settings: Settings = BaritoneAPI.getSettings()
    @JvmStatic
    val primary: IBaritone = baritone.primaryBaritone

    val isPathing: Boolean
        get() = primary.pathingBehavior.isPathing

    val isActive: Boolean
        get() = primary.customGoalProcess.isActive || primary.pathingBehavior.isPathing || primary.pathingControlManager.mostRecentInControl()
            .orElse(null)?.isActive == true

    fun cancel() = primary.pathingBehavior.cancelEverything()
}
