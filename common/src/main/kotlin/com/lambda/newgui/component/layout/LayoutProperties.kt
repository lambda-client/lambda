package com.lambda.newgui.component.layout

class LayoutProperties {
    /**
     * If true, interactions pass through to elements beneath this one.
     */
    var interactionPassthrough = false

    /**
     * If true, this element's rectangle is clamped within parent's bounds.
     */
    var clampPosition = false

    /**
     * If true, anything drawn onto this render layer are clipped within this rect.
     */
    var scissor = false
}