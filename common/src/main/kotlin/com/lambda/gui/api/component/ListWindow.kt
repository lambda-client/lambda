package com.lambda.gui.api.component

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.button.ListButton
import com.lambda.gui.impl.clickgui.AbstractClickGui
import com.lambda.module.modules.client.ClickGui

abstract class ListWindow <T : ListButton> (
    owner: AbstractClickGui
) : WindowComponent<T>(owner) {
    override fun onEvent(e: GuiEvent) {
        if (e is GuiEvent.Tick) {
            contentComponents.children.forEachIndexed { i, button ->
                button.heightOffset = i * (ClickGui.buttonHeight + ClickGui.buttonStep)
            }
        }

        super.onEvent(e)
    }
}