package com.lambda.gui

import com.lambda.config.Configurable
import com.lambda.config.configurations.GuiConfig
import com.lambda.core.Loadable
import com.lambda.gui.impl.clickgui.LambdaClickGui
import com.lambda.gui.impl.clickgui.windows.tag.CustomModuleWindow
import com.lambda.gui.impl.clickgui.windows.tag.TagWindow
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.Vec2d

object GuiConfigurable : Configurable(GuiConfig), Loadable {
    override val name = "gui"
    private val ownerGui = LambdaClickGui

    val mainWindows by setting("windows", defaultWindows).apply {
        onValueChange { _, _ ->
            ownerGui.updateWindows()
        }
    }

    val customWindows by setting("custom windows", listOf<CustomModuleWindow>()).apply {
        onValueChange { _, _ ->
            ownerGui.updateWindows()
        }
    }

    private val defaultWindows get() =
        ModuleTag.defaults.mapIndexed { index, tag ->
            TagWindow(tag, ownerGui).apply {
                val step = 3.0
                position = Vec2d((width + step) * index, 0.0) + step
            }
        }
}
