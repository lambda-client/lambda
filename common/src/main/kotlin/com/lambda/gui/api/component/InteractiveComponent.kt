package com.lambda.gui.api.component

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.IComponent
import com.lambda.util.Mouse

abstract class InteractiveComponent : IComponent {
    protected var hovered = false
    protected var activeButton: Mouse.Button? = null

    protected open fun onPress(e: GuiEvent.MouseClick) {}
    protected open fun onRelease(e: GuiEvent.MouseClick) {}

    override fun onEvent(e: GuiEvent) {
        when (e) {
            is GuiEvent.Show -> {
                hovered = false
                activeButton = null
            }

            is GuiEvent.MouseMove -> {
                hovered = rect.contains(e.mouse)
            }

            is GuiEvent.MouseClick -> {
                hovered = rect.contains(e.mouse)

                val prevPressed = activeButton != null
                activeButton = if (hovered && e.button.isMainButton && e.action == Mouse.Action.Click) e.button else null
                val pressed = activeButton != null

                if (prevPressed == pressed) return
                if (pressed) onPress(e)
                else onRelease(e)
            }
        }
    }
}