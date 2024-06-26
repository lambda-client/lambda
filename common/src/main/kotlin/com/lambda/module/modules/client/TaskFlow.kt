package com.lambda.module.modules.client

import com.lambda.config.groups.InteractionSettings
import com.lambda.config.groups.BuildSettings
import com.lambda.config.groups.RotationSettings
import com.lambda.graphics.renderer.esp.ChunkedESP.Companion.newChunkedESP
import com.lambda.graphics.renderer.esp.EspRenderer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.mainThread
import com.lambda.util.BlockUtils.allSigns
import com.lambda.util.item.ItemUtils

object TaskFlow : Module(
    name = "TaskFlow",
    description = "Settings for task automation",
    defaultTags = setOf(ModuleTag.CLIENT, ModuleTag.AUTOMATION)
) {
    enum class Page {
        BUILD, ROTATION, INTERACTION, TASKS
    }

    private val page by setting("Page", Page.BUILD)
    val build = BuildSettings(this) {
        page == Page.BUILD
    }
    val rotation = RotationSettings(this) {
        page == Page.ROTATION
    }
    val interact = InteractionSettings(this) {
        page == Page.INTERACTION
    }
    val taskCooldown by setting("Task Cooldown", 0, 0..10000, 10, unit = " ms") {
        page == Page.TASKS
    }
    val disposables by setting("Disposables", ItemUtils.defaultDisposables)
    val ignoredBlocks by setting("Ignored Blocks", allSigns)

    val esp by mainThread {
        EspRenderer()
    }
}