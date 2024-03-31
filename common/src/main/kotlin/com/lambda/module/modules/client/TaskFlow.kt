package com.lambda.module.modules.client

import com.lambda.config.InteractionSettings
import com.lambda.config.RotationSettings
import com.lambda.module.Module

object TaskFlow : Module(
    name = "TaskFlow",
    description = "Settings for task automation"
) {
    val rotationSettings = RotationSettings(this)
    val interactionSettings = InteractionSettings(this)
    private val itemMoveDelaySetting = setting("Item Move Delay", 5, 0..20, unit = "ticks")
//    val disposables by setting("Disposables", ItemUtils.defaultDisposables)

    val itemMoveDelay: Long
        get() = itemMoveDelaySetting.value * 50L
}