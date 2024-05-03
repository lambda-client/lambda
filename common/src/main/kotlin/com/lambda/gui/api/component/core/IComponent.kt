package com.lambda.gui.api.component.core

import com.lambda.gui.api.GuiEvent

interface IComponent {
    fun onEvent(e: GuiEvent)
}