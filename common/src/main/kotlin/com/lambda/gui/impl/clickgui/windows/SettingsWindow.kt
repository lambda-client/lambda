package com.lambda.gui.impl.clickgui.windows

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.ListWindow
import com.lambda.gui.api.component.button.ListButton
import com.lambda.gui.impl.clickgui.AbstractClickGui
import com.lambda.gui.impl.clickgui.buttons.ModuleButton

class SettingsWindow(
    button: ModuleButton,
    owner: AbstractClickGui
) : ListWindow<ListButton>(owner) {
    private val module = button.module

    override val title = module.name
    override var width = button.owner.width
    override var height = 0.0

    override fun onEvent(e: GuiEvent) {
        if (e is GuiEvent.Tick) {
            height = contentComponents.children.sumOf { it.size.y }
        }

        super.onEvent(e)
    }
}