package com.lambda.gui

import com.lambda.gui.impl.clickgui.LambdaClickGui
import com.lambda.gui.impl.clickgui.windows.tag.CustomModuleWindow
import com.lambda.module.tag.ModuleTag

object GuiConfigurable : AbstractGuiConfigurable(
    LambdaClickGui, ModuleTag.defaults, "gui"
) {
    var customWindows by setting("custom windows", listOf<CustomModuleWindow>())
}
