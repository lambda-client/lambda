package com.lambda.module.modules.client

import com.lambda.config.InteractionSettings
import com.lambda.config.RotationSettings
import com.lambda.module.Module
import com.lambda.util.BlockUtils.allSigns
import net.minecraft.block.Block

object TaskFlow : Module(
    name = "TaskFlow",
    description = "Settings for task automation"
) {
    val rotationSettings = RotationSettings(this)
    val interactionSettings = InteractionSettings(this)
//    val disposables by setting("Disposables", ItemUtils.defaultDisposables)
    val ignoredBlocks = mutableSetOf<Block>().apply { addAll(allSigns) }
}