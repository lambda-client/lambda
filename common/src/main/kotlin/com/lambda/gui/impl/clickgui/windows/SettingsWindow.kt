package com.lambda.gui.impl.clickgui.windows

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.ListWindow
import com.lambda.gui.api.component.button.ListButton
import com.lambda.gui.impl.clickgui.AbstractClickGui
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.module.modules.client.ClickGui

class SettingsWindow(
    val button: ModuleButton,
    owner: AbstractClickGui
) : ListWindow<ListButton>(owner) {
    private val module = button.module

    override val title = module.name
    override var width = button.owner.width
    override var height = 0.0

    override val showAnimation by animation.exp(0.0, 1.0, ClickGui.openSpeed, ::isOpen)

    override fun onEvent(e: GuiEvent) {
        if (e is GuiEvent.Tick) {
            height = contentComponents.children.sumOf { it.size.y }
            if (showAnimation < 0.05 && !isOpen) destroy()
        }

        super.onEvent(e)
    }
}