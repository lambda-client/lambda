package com.lambda.gui.api.component

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.button.ListButton
import com.lambda.gui.impl.AbstractClickGui

abstract class ListWindow <T : ListButton> (
    owner: AbstractClickGui
) : WindowComponent<T>(owner) {
    override fun onEvent(e: GuiEvent) {
        if (e is GuiEvent.Tick) {
            var y = 0.0
            contentComponents.children.forEach { button ->
                button.heightOffset = y
                y += button.size.y + button.listStep
            }
        }

        super.onEvent(e)
    }
}