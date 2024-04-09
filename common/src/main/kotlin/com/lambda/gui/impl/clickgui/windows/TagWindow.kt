package com.lambda.gui.impl.clickgui.windows

import com.lambda.gui.api.LambdaGui
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.module.modules.client.ClickGui

class TagWindow(override val owner: LambdaGui) : WindowComponent<ModuleButton>(owner) {
    override val title = "Test Window"

    // TODO: resizing
    override var width = 110.0
    override var height = 300.0

    override fun onRender() {
        updateModules()
        super.onRender()
    }

    private fun updateModules() {
        children.sortBy { it.module.name }

        children.forEachIndexed { i, button ->
            button.heightOffset = i * (ClickGui.buttonHeight + ClickGui.buttonStep)
        }
    }
}