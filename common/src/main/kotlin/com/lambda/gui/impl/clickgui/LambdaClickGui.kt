package com.lambda.gui.impl.clickgui

import com.lambda.gui.impl.clickgui.windows.tag.CustomTagWindow

object LambdaClickGui : AbstractClickGui() {
    override fun onShow() {
        updateWindows()
        super.onShow()
    }

    override fun onTick() {
        updateWindows()
        super.onTick()
    }

    private fun updateWindows() {
        val windows = GuiConfigurable.mainWindows + GuiConfigurable.customWindows
        val new = windows.subtract(children)
        children.addAll(new)

        children.removeIf {
            if (it !is CustomTagWindow) return@removeIf false

            val flag = it !in windows
            if (flag) it.destroy()
            flag
        }
    }
}