package com.lambda.gui.impl.clickgui.windows.tag

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.impl.clickgui.AbstractClickGui
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.gui.impl.clickgui.windows.ModuleWindow
import com.lambda.module.Module

class CustomModuleWindow(
    override var title: String = "Untitled",
    val modules: MutableList<Module> = mutableListOf(),
    gui: AbstractClickGui
) : ModuleWindow(title, gui = gui) {
    override fun onEvent(e: GuiEvent) {
        if (e is GuiEvent.Tick) updateModules()
        super.onEvent(e)
    }

    private fun updateModules() {
        contentComponents.apply {
            // Add missing module buttons
            modules.filter { module ->
                children.all { button ->
                    button.module != module
                }
            }.map { ModuleButton(it, contentComponents) }
                .forEach(contentComponents::addChild)

            // Remove deleted modules
            children.forEach { button ->
                if (button.module !in modules) {
                    this@CustomModuleWindow.gui.scheduleAction {
                        removeChild(button)
                    }
                }
            }
        }
    }
}