package com.lambda.gui.impl.clickgui.windows.tag

import com.lambda.gui.impl.AbstractClickGui
import com.lambda.gui.impl.clickgui.windows.ModuleWindow
import com.lambda.gui.impl.hudgui.LambdaHudGui
import com.lambda.module.HudModule
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.module.tag.ModuleTag

class TagWindow(
    val tag: ModuleTag,
    owner: AbstractClickGui,
) : ModuleWindow(tag.name, gui = owner) {
    val isHudWindow = gui is LambdaHudGui
    private val rawFilter = { m: Module -> m is HudModule }
    private val filter get() = if (isHudWindow) rawFilter else { m: Module -> !rawFilter(m) }

    override fun getModuleList() = ModuleRegistry.modules
        .filter { it.defaultTags.firstOrNull() == tag && filter(it) }
}