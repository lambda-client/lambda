package com.lambda.gui.api.component.core.list

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.InteractiveComponent
import com.lambda.gui.api.component.core.IComponent

abstract class ChildComponent : InteractiveComponent() {
    abstract val owner: IComponent
    open var accessible = false

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        if (e is GuiEvent.MouseMove) hovered = hovered && accessible
    }

    open fun onAdd() {}
    open fun onRemove() {}
}
