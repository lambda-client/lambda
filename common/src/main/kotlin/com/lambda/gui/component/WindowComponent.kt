package com.lambda.gui.component

import com.lambda.gui.layer.RenderLayer
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d

abstract class WindowComponent : IRectComponent {
    override var position = Vec2d.ZERO to Vec2d.ZERO

    abstract val title: String
    val render = RenderLayer()

    override fun onShow() {

    }

    override fun onHide() {

    }

    override fun onRender() {

    }

    override fun onKey(key: KeyCode) {

    }

    override fun onMouse(button: Mouse.Button, action: Mouse.Action) {

    }
}