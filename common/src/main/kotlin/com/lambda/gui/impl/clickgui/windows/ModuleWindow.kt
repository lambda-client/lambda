package com.lambda.gui.impl.clickgui.windows

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.ListWindow
import com.lambda.gui.impl.clickgui.AbstractClickGui
import com.lambda.gui.impl.clickgui.buttons.ModuleButton

abstract class ModuleWindow(
    override var title: String,
    override var width: Double = 110.0,
    override var height: Double = 300.0,
    owner: AbstractClickGui
) : ListWindow<ModuleButton>(owner) {
    override fun onEvent(e: GuiEvent) {
        if (e is GuiEvent.Tick) {
            contentComponents.children.sortBy {
                it.module.name
            }
        }

        super.onEvent(e)
    }
}