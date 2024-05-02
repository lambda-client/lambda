package com.lambda.gui.impl.clickgui

import com.lambda.gui.GuiConfigurable
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.impl.clickgui.windows.tag.CustomModuleWindow

object LambdaClickGui : AbstractClickGui() {
    override fun onEvent(e: GuiEvent) {
        if (e is GuiEvent.Show || e is GuiEvent.Tick) updateWindows()
        super.onEvent(e)
    }

    fun updateWindows() {
        windows.apply {
            val windows = GuiConfigurable.mainWindows + GuiConfigurable.customWindows
            children.addAll(windows.subtract(children.toSet()))

            children.filter { it !in windows && it is CustomModuleWindow }
                .forEach(WindowComponent<*>::destroy)
        }
    }
}