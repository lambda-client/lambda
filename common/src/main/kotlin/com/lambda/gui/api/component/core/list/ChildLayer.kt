package com.lambda.gui.api.component.core.list

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.IComponent
import com.lambda.util.Mouse

open class ChildLayer <T : ChildComponent> (val childAccessible: (T) -> Boolean) : IComponent {
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
                    child.accessible = childAccessible(child)
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
}