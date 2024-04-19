package com.lambda.gui.api.component.core.list

import com.lambda.gui.api.component.core.IComponent
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d

interface IListComponent <T : IComponent> : IComponent {
    val children: List<T>

    fun isChildAccessible(child: T): Boolean = (child as? ChildComponent)?.accessible ?: true

    override fun onShow() {
        children.forEach(IComponent::onShow)
    }

    override fun onHide() {
        children.forEach(IComponent::onHide)
    }

    override fun onTick() {
        children.forEach(IComponent::onTick)
    }

    override fun onRender() {
        children.forEach(IComponent::onRender)
    }

    override fun onKey(key: KeyCode) {
        children.filter(::isChildAccessible).forEach { child ->
            child.onKey(key)
        }
    }

    override fun onChar(char: Char) {
        children.filter(::isChildAccessible).forEach { child ->
            child.onChar(char)
        }
    }

    override fun onMouseClick(button: Mouse.Button, action: Mouse.Action, mouse: Vec2d) {
        children.forEach { child ->
            val newAction = if (isChildAccessible(child)) action else Mouse.Action.Release
            child.onMouseClick(button, newAction, mouse)
        }
    }

    override fun onMouseMove(mouse: Vec2d) {
        children.forEach { child ->
            child.onMouseMove(mouse)
        }
    }
}