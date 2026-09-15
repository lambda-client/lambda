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

package com.lambda.module.hud

import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.imgui.flag.ImGuiCol
import com.lambda.module.HudModule
import com.lambda.module.ModuleRegistry
import com.lambda.module.ModuleTag
import java.awt.Color

@Suppress("unused")
object ModuleList : HudModule(
    name = "ModuleList",
    tag = ModuleTag.HUD,
) {
	val onlyBound by setting("Only Bound", false, "Only displays modules with a keybind")
	val showKeybind by setting("Show Keybind", true, "Display keybind next to a module")

    init {
        drawSetting.value = false
    }

    override fun ImGuiBuilder.buildLayout() {
        val enabled = ModuleRegistry.modules.filter { it.isEnabled && it.draw }

        enabled.forEach {
            val bound = it.keybind.key != 0 || it.keybind.mouse != -1
            if (onlyBound && !bound) return@forEach
            text(it.name)

	        if (showKeybind) {
		        val color = if (!bound) Color.RED else Color.GREEN

		        sameLine()
		        withStyleColor(ImGuiCol.Text, color) { text(" [${it.keybind.name}]") }
	        }
        }
    }
}
