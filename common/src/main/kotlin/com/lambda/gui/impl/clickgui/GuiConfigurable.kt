package com.lambda.gui.impl.clickgui

import com.google.gson.reflect.TypeToken
import com.lambda.config.Configurable
import com.lambda.config.configurations.GuiConfig
import com.lambda.gui.impl.clickgui.windows.TagWindow

object GuiConfigurable : Configurable(GuiConfig) {
    override val name = "gui"
    val windows = setting("windows", listOf(TagWindow()), object : TypeToken<List<TagWindow>>() {}.type)
}