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

package com.lambda.gui.components

import com.lambda.core.Loadable
import com.lambda.event.events.GuiEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.gui.MenuBar.buildMenuBar
import com.lambda.gui.components.QuickSearch.renderQuickSearch
import com.lambda.gui.dsl.ImGuiBuilder.buildLayout
import com.lambda.module.ModuleRegistry
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.tag.ModuleTag.Companion.shownTags
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags.AlwaysAutoResize

object ClickGuiLayout : Loadable {
    init {
        listen<GuiEvent.NewFrame> {
            if (!ClickGui.isEnabled) return@listen

            buildLayout {
                shownTags.forEach { tag ->
                    window(tag.name, flags = AlwaysAutoResize) {
                        ModuleRegistry.modules
                            .filter { it.tag == tag }
                            .forEach { with(ModuleEntry(it)) { buildLayout() } }
                    }
                }

                buildMenuBar()
                renderQuickSearch()

                ImGui.showDemoWindow()
            }
        }
    }
}
