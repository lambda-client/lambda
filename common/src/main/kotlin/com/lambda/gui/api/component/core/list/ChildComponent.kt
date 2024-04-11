package com.lambda.gui.api.component.core.list

import com.lambda.gui.api.component.InteractiveComponent

abstract class ChildComponent : InteractiveComponent(), IChildComponent {
    // mostly used to create an animation when an element appears
    var accessible = false; set(value) {
        if (field == value) return
        field = value

        if (value) onShow()
        else onHide()
    }
}
