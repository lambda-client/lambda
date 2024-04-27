package com.lambda.gui.impl.clickgui

import com.lambda.config.Configurable
import com.lambda.config.configurations.GuiConfig
import com.lambda.core.Loadable
import com.lambda.gui.impl.clickgui.windows.TagWindow

object GuiConfigurable : Configurable(GuiConfig), Loadable {
    override val name = "gui"
    val windows = setting("windows", listOf(TagWindow()))
}
