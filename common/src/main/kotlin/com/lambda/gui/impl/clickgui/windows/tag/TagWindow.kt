package com.lambda.gui.impl.clickgui.windows.tag

import com.lambda.gui.impl.AbstractClickGui
import com.lambda.gui.impl.clickgui.windows.ModuleWindow
import com.lambda.module.ModuleRegistry
import com.lambda.module.tag.ModuleTag

class TagWindow(
    val tag: ModuleTag,
    owner: AbstractClickGui,
) : ModuleWindow(tag.name, gui = owner) {
    override fun getModuleList() = ModuleRegistry.modules
        .filter { it.defaultTags.firstOrNull() == tag }
}