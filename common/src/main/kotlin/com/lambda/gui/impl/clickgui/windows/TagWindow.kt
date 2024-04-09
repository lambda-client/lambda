package com.lambda.gui.impl.clickgui.windows

import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.module.ModuleRegistry
import com.lambda.module.modules.client.ClickGui

class TagWindow : WindowComponent<ModuleButton>() {
    override val title = "Test Window"
    override var width = 110.0
    override var height = 300.0

    init {
        ModuleRegistry.modules.forEach {
            children.add(ModuleButton(it, this))
        }
    }

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