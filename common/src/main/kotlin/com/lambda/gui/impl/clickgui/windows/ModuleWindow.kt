package com.lambda.gui.impl.clickgui.windows

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.ListWindow
import com.lambda.gui.impl.clickgui.AbstractClickGui
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.module.Module

abstract class ModuleWindow(
    override var title: String,
    override var width: Double = 110.0,
    override var height: Double = 300.0,
    gui: AbstractClickGui,
) : ListWindow<ModuleButton>(gui) {
    private var lastUpdate = 0L

    abstract fun getModuleList(): Collection<Module>

    private fun updateModules() {
        val time = System.currentTimeMillis()
        if (time - lastUpdate < 1000L) return
        lastUpdate = time

        contentComponents.apply {
            val modules = getModuleList()

            // Add missing module buttons
            modules.filter { module ->
                children.all { button ->
                    button.module != module
                }
            }.map { ModuleButton(it, contentComponents) }
                .forEach(contentComponents.children::add)

            // Remove deleted modules
            children.forEach { button ->
                if (button.module !in modules) {
                    this@ModuleWindow.gui.scheduleAction {
                        children.remove(button)
                    }
                }
            }
        }
    }

    override fun onEvent(e: GuiEvent) {
        if (e is GuiEvent.Show || e is GuiEvent.Tick) updateModules()

        if (e is GuiEvent.Tick) {
            contentComponents.children.sortBy {
                it.module.name
            }
        }

        super.onEvent(e)
    }
}