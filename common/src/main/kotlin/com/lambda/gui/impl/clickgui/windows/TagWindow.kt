package com.lambda.gui.impl.clickgui.windows

import com.lambda.gui.api.LambdaGui
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.api.component.core.list.IChildComponent
import com.lambda.gui.impl.clickgui.LambdaClickGui
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.module.ModuleRegistry
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.tag.ModuleTag

class TagWindow(
    val tags: Set<ModuleTag> = setOf(),
    override var title: String = "Untitled",
    override var width: Double = 110.0,
    override var height: Double = 300.0,
    override val owner: LambdaGui = LambdaClickGui
) : WindowComponent<ModuleButton>(), IChildComponent {
    init {
        ModuleRegistry.modules.filter { module ->
            module.tags.any(tags::contains)
        }.forEach {
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