package com.lambda.gui.impl.clickgui

import com.lambda.config.Configurable
import com.lambda.config.configurations.GuiConfig
import com.lambda.gui.impl.clickgui.windows.TagWindow
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.Vec2d

object GuiConfigurable : Configurable(GuiConfig) {
    override val name = "gui"
    val windows = setting("windows", defaultWindows)

    private val defaultWindows get() =
        ModuleTag.defaults.mapIndexed { index, tag ->
            TagWindow(setOf(tag), tag.name).apply {
                val step = 3.0
                position = Vec2d((width + step) * index, 0.0) + step
            }
        }
}