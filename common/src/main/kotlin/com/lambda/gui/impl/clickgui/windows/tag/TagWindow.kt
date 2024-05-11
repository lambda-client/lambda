package com.lambda.gui.impl.clickgui.windows.tag

import com.lambda.gui.impl.clickgui.AbstractClickGui
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.gui.impl.clickgui.windows.ModuleWindow
import com.lambda.module.ModuleRegistry
import com.lambda.module.tag.ModuleTag

class TagWindow(
    val tag: ModuleTag,
    owner: AbstractClickGui
) : ModuleWindow(tag.name, gui = owner) {
    init {
        ModuleRegistry.modules
            .filter { it.defaultTags.firstOrNull() == tag }
            .map { ModuleButton(it, contentComponents) }
            .forEach(contentComponents::addChild)
    }
}