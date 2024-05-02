package com.lambda.gui.api.component

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.IComponent
import com.lambda.gui.api.component.core.IRectComponent
import com.lambda.util.Mouse

abstract class InteractiveComponent : IComponent, IRectComponent {
    protected var hovered = false
    protected var pressed = false; set(value) {
        if (field == value) return
        field = value

        if (value) onPress()
        else onRelease()
    }

    protected var activeMouseButton: Mouse.Button? = null

    protected open fun onPress() {}
    protected open fun onRelease() {}

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
                activeMouseButton = e.button.takeUnless {
                    it.isMainButton && e.action == Mouse.Action.Click
                }

                pressed = hovered && e.button.isMainButton && e.action == Mouse.Action.Click
            }
        }
    }
}