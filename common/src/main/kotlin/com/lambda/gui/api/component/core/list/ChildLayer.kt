package com.lambda.gui.api.component.core.list

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.LambdaGui
import com.lambda.gui.api.component.core.IComponent
import com.lambda.gui.api.layer.RenderLayer
import com.lambda.util.Mouse
import com.lambda.util.math.Rect

open class ChildLayer <T : ChildComponent, R : IComponent> (
    val gui: LambdaGui,
    val ownerComponent: R,
    private val childRect: () -> Rect,
    private val childAccessible: (T) -> Boolean = { true }
) : IComponent {
    override val isActive get() = ownerComponent.isActive
    override val childShowAnimation get() = ownerComponent.childShowAnimation
    override val rect get() = childRect()
    val children = mutableListOf<T>()

    fun addChild(child : T) {
        children.add(child)
        child.onAdd()
    }

    fun removeChild(child : T) {
        children.remove(child)
        child.onRemove()
    }

    override fun onEvent(e: GuiEvent) {
        children.forEach { child ->
            when (e) {
                is GuiEvent.Tick -> {
                    val ownerAccessible = (ownerComponent as? ChildComponent)?.accessible ?: true
                    child.accessible = childAccessible(child) && child.rect in rect && ownerAccessible && ownerComponent.isActive
                }

                is GuiEvent.KeyPress, is GuiEvent.CharTyped -> {
                    if (!child.accessible) return@forEach
                }

                is GuiEvent.MouseClick -> {
                    val newAction = if (child.accessible) e.action else Mouse.Action.Release
                    val newEvent = GuiEvent.MouseClick(e.button, newAction, e.mouse)
                    child.onEvent(newEvent)
                    return@forEach
                }
            }

            child.onEvent(e)
        }
    }

    class Drawable <T : ChildComponent, R : IComponent>  (
        gui: LambdaGui,
        owner: R,
        val renderer: RenderLayer,
        contentRect: () -> Rect,
        childAccessible: (T) -> Boolean = { true }
    ) : ChildLayer<T, R>(gui, owner, contentRect, childAccessible)
}