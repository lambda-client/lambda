package com.lambda.gui.api.component

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.IComponent
import com.lambda.gui.api.component.core.IRectComponent
import com.lambda.util.Mouse

abstract class InteractiveComponent : IComponent, IRectComponent {
    protected var hovered = false
    protected var pressed = false

    protected open fun onPress(e: GuiEvent.MouseClick) {}
    protected open fun onRelease(e: GuiEvent.MouseClick) {}

    override fun onEvent(e: GuiEvent) {
        when (e) {
            is GuiEvent.Show -> {
                hovered = false
                pressed = false
            }

            is GuiEvent.MouseMove -> {
                hovered = rect.contains(e.mouse)
            }

            is GuiEvent.MouseClick -> {
                val prevPressed = pressed
                pressed = hovered && e.button.isMainButton && e.action == Mouse.Action.Click

                if (prevPressed == pressed) return
                if (pressed) onPress(e)
                else onRelease(e)
            }
        }
    }
}