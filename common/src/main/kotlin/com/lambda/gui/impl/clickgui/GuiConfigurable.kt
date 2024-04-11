package com.lambda.gui.impl.clickgui

import com.lambda.config.Configurable
import com.lambda.config.configurations.GuiConfig
import com.lambda.gui.impl.clickgui.windows.TagWindow
import com.lambda.module.tag.ModuleTag

object GuiConfigurable : Configurable(GuiConfig) {
    override val name = "gui"
    val windows = setting("windows", listOf(TagWindow(ModuleTag("Test Window"))))
}