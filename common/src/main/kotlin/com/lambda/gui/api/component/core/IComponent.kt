package com.lambda.gui.api.component.core

import com.lambda.gui.api.GuiEvent

interface IComponent {
    // TODO: Use event system?
    fun onEvent(e: GuiEvent)
}