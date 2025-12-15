/*
 * Copyright 2025 Lambda
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

package com.lambda.module.hud

import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.module.HudModule
import com.lambda.module.ModuleRegistry
import com.lambda.module.tag.ModuleTag
import com.lambda.util.KeyCode
import imgui.flag.ImGuiCol
import java.awt.Color

object ModuleList : HudModule(
    name = "ModuleList",
    tag = ModuleTag.HUD,
) {
	val showKeybind by setting("Show Keybind", true, "Display keybind next to a module")

    override val isVisible: Boolean
        get() = false

    override fun ImGuiBuilder.buildLayout() {
        val enabled = ModuleRegistry.modules
            .filter { it.isEnabled }
            .filter { it.isVisible }

        enabled.forEach {
            text(it.name); sameLine()

	        if (showKeybind) {
		        val color = if (it.keybind.key == 0 && it.keybind.mouse == -1) Color.RED else Color.GREEN
		        withStyleColor(ImGuiCol.Text, color) { text(" [${it.keybind.name}]") }
	        }
        }
    }
}
