package com.lambda.gui.impl.clickgui

import com.lambda.command.CommandManager.setting
import com.lambda.config.Configurable
import com.lambda.config.configurations.GuiConfig
import com.lambda.gui.api.LambdaGui
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.api.component.core.list.IListComponent
import com.lambda.gui.impl.clickgui.windows.TagWindow
import com.lambda.module.modules.client.ClickGui

object LambdaClickGui : LambdaGui("ClickGui", ClickGui), IListComponent<WindowComponent<*>> {
    private val windows = setting("windows", mutableListOf<WindowComponent<*>>(TagWindow()))
    override val children: MutableList<WindowComponent<*>> get() = windows.value

    init {
        object : Configurable(GuiConfig) {
            init { settings.add(windows) }

            override val name = this@LambdaClickGui.name
        }
    }
}