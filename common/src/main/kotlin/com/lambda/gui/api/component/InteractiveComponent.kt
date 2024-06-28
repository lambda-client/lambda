package com.lambda.gui.api.component

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.IComponent
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d

abstract class InteractiveComponent : IComponent {
    protected open val hovered get() = rect.contains(lastMouse)
    protected var activeButton: Mouse.Button? = null

    protected open fun onPress(e: GuiEvent.MouseClick) {}
    protected open fun onRelease(e: GuiEvent.MouseClick) {}

    private var lastMouse = Vec2d.ZERO

    override fun onEvent(e: GuiEvent) {
        when (e) {
            is GuiEvent.Show -> {
                lastMouse = Vec2d.ONE * -1000.0
                activeButton = null
            }

            is GuiEvent.MouseMove -> {
                lastMouse = e.mouse
            }

            is GuiEvent.MouseClick -> {
                lastMouse = e.mouse

                val prevPressed = activeButton != null
                activeButton =
                    if (hovered && e.button.isMainButton && e.action == Mouse.Action.Click) e.button else null
                val pressed = activeButton != null

                if (prevPressed == pressed) return
                if (pressed) onPress(e)
                else onRelease(e)
            }
        }
    }
}