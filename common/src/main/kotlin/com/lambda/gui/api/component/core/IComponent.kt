package com.lambda.gui.api.component.core

import com.lambda.gui.api.GuiEvent
import com.lambda.util.math.Rect

interface IComponent {
    val isActive: Boolean get() = true
    val showAnimation: Double get() = 1.0
    val rect: Rect

    fun onEvent(e: GuiEvent)
}