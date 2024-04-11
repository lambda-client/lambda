package com.lambda.gui.impl.clickgui.windows

import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.module.ModuleRegistry
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.tag.ModuleTag

class TagWindow(
    val tag: ModuleTag,
    override var title: String = tag.name,
    override var width: Double = 110.0,
    override var height: Double = 300.0
) : WindowComponent<ModuleButton>() {
    init {
        ModuleRegistry.modules/*.filter { module ->
            module.customTags.value.any { it.name.equals(tag.name, true) }
        }*/.forEach {
            children.add(ModuleButton(it, this))
        }
    }

    override fun onRender() {
        updateModules()
        super.onRender()
    }

    private fun updateModules() {
        children.sortBy { it.module.name }

        // ToDo: Update tag filter

        children.forEachIndexed { i, button ->
            button.heightOffset = i * (ClickGui.buttonHeight + ClickGui.buttonStep)
        }
    }
}