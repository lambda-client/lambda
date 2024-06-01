package com.lambda.module.modules.client

import com.lambda.config.groups.InteractionSettings
import com.lambda.config.groups.BuildSettings
import com.lambda.config.groups.RotationSettings
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.BlockUtils.allSigns
import net.minecraft.block.Block

object TaskFlow : Module(
    name = "TaskFlow",
    description = "Settings for task automation",
    defaultTags = setOf(ModuleTag.CLIENT, ModuleTag.AUTOMATION)
) {
    enum class Page {
        BUILD, ROTATION, INTERACTION
    }

    private val page by setting("Page", Page.BUILD)
    val buildSettings = BuildSettings(this) {
        page == Page.BUILD
    }
    val rotationSettings = RotationSettings(this) {
        page == Page.ROTATION
    }
    val interactionSettings = InteractionSettings(this) {
        page == Page.INTERACTION
    }
//    val disposables by setting("Disposables", ItemUtils.defaultDisposables)
    val ignoredBlocks = mutableSetOf<Block>().apply { addAll(allSigns) }
}