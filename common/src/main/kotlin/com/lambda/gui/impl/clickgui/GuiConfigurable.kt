package com.lambda.gui.impl.clickgui

import com.lambda.config.Configurable
import com.lambda.config.configurations.GuiConfig
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.impl.clickgui.windows.TagWindow

object GuiConfigurable : Configurable(GuiConfig) {
    override val name = "ClickGui"
    val windows = setting("windows", mutableListOf<WindowComponent<*>>(TagWindow()))
}