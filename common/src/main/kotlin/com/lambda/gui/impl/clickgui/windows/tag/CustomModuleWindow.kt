package com.lambda.gui.impl.clickgui.windows.tag

import com.lambda.gui.impl.clickgui.AbstractClickGui
import com.lambda.gui.impl.clickgui.windows.ModuleWindow
import com.lambda.module.Module

class CustomModuleWindow(
    override var title: String = "Untitled",
    val modules: MutableList<Module> = mutableListOf(),
    gui: AbstractClickGui,
) : ModuleWindow(title, gui = gui) {
    override fun getModuleList() = modules
}