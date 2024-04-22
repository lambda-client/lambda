package com.lambda.gui

import com.lambda.config.Configurable
import com.lambda.config.configurations.GuiConfig
import com.lambda.core.Loadable
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.impl.clickgui.LambdaClickGui
import com.lambda.gui.impl.clickgui.windows.tag.CustomModuleWindow
import com.lambda.gui.impl.clickgui.windows.tag.TagWindow
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.Vec2d

object GuiConfigurable : Configurable(GuiConfig), Loadable {
    override val name = "gui"

    val mainWindows by setting("windows", defaultWindows).apply {
        listener { from, _ ->
            from.forEach(WindowComponent<*>::destroy) // free vram
        }
    }

    val customWindows by setting("custom windows", listOf<CustomModuleWindow>()).apply {
        listener { from, _ ->
            from.forEach(WindowComponent<*>::destroy) // free vram
        }
    }

    private val defaultWindows get() =
        ModuleTag.defaults.mapIndexed { index, tag ->
            TagWindow(tag, LambdaClickGui).apply {
                val step = 3.0
                position = Vec2d((width + step) * index, 0.0) + step
            }
        }
}
