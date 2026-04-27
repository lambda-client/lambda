/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.gui.components

import com.lambda.gui.Layout
import com.lambda.gui.components.SettingsWidget.buildConfigSettingsContext
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImGui
import com.lambda.module.Module

class ModuleEntry(val module: Module): Layout {
    override fun ImGuiBuilder.buildLayout() {
        selectable(module.name, selected = module.isEnabled) {
            module.toggle()
        }
        lambdaTooltip(module.description)

        ImGui.setNextWindowSizeConstraints(0f, 0f, Float.MAX_VALUE, io.displaySize.y * 0.5f)
        popupContextItem("##ctx-${module.name}") {
            buildConfigSettingsContext(module)
        }
    }
}