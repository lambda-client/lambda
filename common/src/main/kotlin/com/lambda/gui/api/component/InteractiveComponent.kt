package com.lambda.gui.api.component

import com.lambda.gui.api.component.core.IComponent
import com.lambda.gui.api.component.core.IRectComponent
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d

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

    override fun onShow() {
        hovered = false
        pressed = false
    }

    override fun onMouseMove(mouse: Vec2d) {
        hovered = rect.contains(mouse)
    }

    override fun onMouseClick(button: Mouse.Button, action: Mouse.Action, mouse: Vec2d) {
        activeMouseButton = button.takeUnless { it.isMainButton && action == Mouse.Action.Click }
        pressed = hovered && button.isMainButton && action == Mouse.Action.Click
    }
}