package com.lambda.gui

import com.lambda.config.Configurable
import com.lambda.config.configurations.GuiConfig
import com.lambda.core.lifecycle.Loadable
import com.lambda.gui.impl.AbstractClickGui
import com.lambda.gui.impl.clickgui.windows.tag.CustomModuleWindow
import com.lambda.gui.impl.clickgui.windows.tag.TagWindow
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.Vec2d

abstract class AbstractGuiConfigurable(
    private val ownerGui: AbstractClickGui,
    private val tags: Set<ModuleTag>,
    override val name: String
) : Configurable(GuiConfig), Loadable {
    var mainWindows by setting("windows", defaultWindows)
    open var customWindows = mutableListOf<CustomModuleWindow>()

    private val defaultWindows get() =
        tags.mapIndexed { index, tag ->
            TagWindow(tag, ownerGui).apply {
                val step = 5.0
                position = Vec2d((width + step) * index, 0.0) + step
            }
        }
}
