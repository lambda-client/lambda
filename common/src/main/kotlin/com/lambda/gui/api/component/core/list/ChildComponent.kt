package com.lambda.gui.api.component.core.list

import com.lambda.gui.api.component.InteractiveComponent
import com.lambda.gui.api.component.core.IComponent
import com.lambda.util.math.Vec2d

abstract class ChildComponent : InteractiveComponent() {
    abstract val owner: IComponent
    open var accessible = false

    override fun onMouseMove(mouse: Vec2d) {
        super.onMouseMove(mouse)
        hovered = hovered && accessible
    }

    open fun onAdd() {}
    open fun onRemove() {}
}
