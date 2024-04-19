package com.lambda.gui.impl.clickgui.windows.tag

import com.lambda.gui.impl.clickgui.AbstractClickGui
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.gui.impl.clickgui.windows.ModuleWindow
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.module.tag.ModuleTag

class CustomModuleWindow(
    override var title: String = "Untitled",
    val modules: MutableList<Module> = mutableListOf(),
    owner: AbstractClickGui
) : ModuleWindow(title, owner = owner) {
    override fun onTick() {
        updateModules()
        super.onTick()
    }

    private fun updateModules() {
        // Add missing module buttons
        modules.filter { module ->
            children.all { button ->
                button.module != module
            }
        }.map { ModuleButton(it, this) }.forEach(children::add)

        // Remove deleted modules
        children.removeIf {
            val flag = it.module !in modules
            if (flag) it.onRemove()
            flag
        }
    }
}