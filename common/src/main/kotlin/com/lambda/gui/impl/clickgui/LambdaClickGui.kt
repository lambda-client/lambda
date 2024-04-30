package com.lambda.gui.impl.clickgui

import com.lambda.gui.GuiConfigurable
import com.lambda.gui.api.component.WindowComponent

object LambdaClickGui : AbstractClickGui() {
    override fun onShow() {
        updateWindows()
        super.onShow()
    }

    override fun onTick() {
        updateWindows()
        super.onTick()
    }

    fun updateWindows() {
        val windows = GuiConfigurable.mainWindows + GuiConfigurable.customWindows
        val new = windows.subtract(children.toSet())
        children.addAll(new)
        children.removeIf {
            if (it !is WindowComponent<*>) return@removeIf false

            val absent = it !in windows
            if (absent) it.destroy()
            absent
        }
    }
}