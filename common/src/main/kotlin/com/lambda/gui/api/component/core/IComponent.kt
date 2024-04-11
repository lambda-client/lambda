package com.lambda.gui.api.component.core

import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d

interface IComponent {
    fun onShow() {}

    fun onHide() {}

    fun onTick() {}

    fun onRender() {}

    fun onKey(key: KeyCode) {}

    fun onChar(char: Char) {}

    fun onMouseClick(button: Mouse.Button, action: Mouse.Action, mouse: Vec2d) {}

    fun onMouseMove(mouse: Vec2d) {}
}