package com.lambda.gui.impl.clickgui

import com.lambda.gui.GuiConfigurable
import com.lambda.gui.api.GuiEvent

object LambdaClickGui : AbstractClickGui() {
    override fun onEvent(e: GuiEvent) {
        if (e is GuiEvent.Show || e is GuiEvent.Tick) {
            updateWindows()
        }

        super.onEvent(e)
    }

    fun updateWindows() {
        windows.apply {
            val windows = GuiConfigurable.mainWindows + GuiConfigurable.customWindows
            val new = windows.subtract(children.toSet())
            children.addAll(new)

            children.forEach { window ->
                if (window !in windows) {
                    scheduleAction {
                        removeChild(window)
                    }
                }
            }
        }
    }
}