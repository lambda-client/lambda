
package com.minato.gui.components

import com.minato.gui.Layout
import com.minato.gui.components.SettingsWidget.buildConfigSettingsContext
import com.minato.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImGui
import com.minato.module.Module

class ModuleEntry(val module: Module): Layout {
    override fun ImGuiBuilder.buildLayout() {
        selectable(module.name, selected = module.isEnabled) {
            module.toggle()
        }
        minatoTooltip(module.description)

        ImGui.setNextWindowSizeConstraints(0f, 0f, Float.MAX_VALUE, io.displaySize.y * 0.5f)
        popupContextItem("##ctx-${module.name}") {
            buildConfigSettingsContext(module)
        }
    }
}