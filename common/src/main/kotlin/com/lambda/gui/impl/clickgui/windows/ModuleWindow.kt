package com.lambda.gui.impl.clickgui.windows

import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.impl.clickgui.AbstractClickGui
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.module.modules.client.ClickGui

abstract class ModuleWindow(
    override var title: String,
    override var width: Double = 110.0,
    override var height: Double = 300.0,
    owner: AbstractClickGui
) : WindowComponent<ModuleButton>(owner) {
    override fun onTick() {
        children.sortBy {
            it.module.name
        }

        children.forEachIndexed { i, button ->
            button.heightOffset = i * (ClickGui.buttonHeight + ClickGui.buttonStep)
        }

        super.onTick()
    }
}