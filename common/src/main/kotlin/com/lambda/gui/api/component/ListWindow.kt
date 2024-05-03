package com.lambda.gui.api.component

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.button.ListButton
import com.lambda.gui.impl.clickgui.AbstractClickGui
import com.lambda.gui.impl.clickgui.buttons.SettingButton
import com.lambda.module.modules.client.ClickGui

abstract class ListWindow <T : ListButton> (
    owner: AbstractClickGui
) : WindowComponent<T>(owner) {
    override fun onEvent(e: GuiEvent) {
        if (e is GuiEvent.Show || e is GuiEvent.Tick) {
            var y = 0.0
            contentComponents.children.forEach { button ->
                if (button is SettingButton<*, *> && !button.visible) return@forEach
                button.heightOffset = y
                y += button.size.y + ClickGui.buttonStep
            }
        }

        super.onEvent(e)
    }
}