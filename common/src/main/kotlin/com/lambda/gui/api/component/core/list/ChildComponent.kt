package com.lambda.gui.api.component.core.list

import com.lambda.gui.api.component.InteractiveComponent

abstract class ChildComponent(open val owner: ChildLayer<*, *>) : InteractiveComponent() {
    open var accessible = false
    override val hovered; get() = super.hovered && accessible

    open fun onAdd() {}
    open fun onRemove() {}
}
