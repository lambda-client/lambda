package com.lambda.gui.component

import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d

interface IChildComponent <T : IComponent> : IComponent {
   val parent: T
}

interface IListComponent <T : IChildComponent<*>> : IComponent {
    val children: List<T>
}

interface IRectComponent : IComponent {
    val position: Pair<Vec2d, Vec2d>
}

interface IComponent {
    fun onShow()

    fun onHide()

    fun onRender()

    fun onKey(key: KeyCode)

    fun onMouse(button: Mouse.Button, action: Mouse.Action)
}