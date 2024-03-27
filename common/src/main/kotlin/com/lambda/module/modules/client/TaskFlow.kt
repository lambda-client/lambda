package com.lambda.module.modules.client

import com.lambda.module.Module
import com.lambda.util.ItemUtils

object TaskFlow : Module(
    name = "TaskFlow",
    description = "Settings for task automation"
) {
    private val itemMoveDelaySetting = setting("Item Move Delay", 5, 0..20, unit = "ticks")
    val disposables by setting("Disposables", ItemUtils.defaultDisposables)

    val itemMoveDelay: Long
        get() = itemMoveDelaySetting.value * 50L
}