package com.lambda.gui.impl.clickgui.windows.tag

import com.lambda.gui.impl.clickgui.AbstractClickGui
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.gui.impl.clickgui.windows.ModuleWindow
import com.lambda.module.ModuleRegistry
import com.lambda.module.tag.ModuleTag

class TagWindow(
    val tag: ModuleTag,
    owner: AbstractClickGui
) : ModuleWindow(tag.name, owner = owner) {
    init {
        ModuleRegistry.modules
            .filter { it.tag == tag }
            .map { ModuleButton(it, this) }
            .forEach(contentComponents::addChild)
    }
}