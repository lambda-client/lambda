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
import com.lambda.gui.dsl.ImGuiBuilder.buildLayout
import com.lambda.module.ModuleRegistry
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.tag.ModuleTag
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags.AlwaysAutoResize

object ClickGuiLayout : Loadable {
    private val shownTags = ModuleTag.defaults.toMutableList()

    init {
        listen<GuiEvent.NewFrame> {
            if (!ClickGui.isEnabled) return@listen

            buildLayout {
                shownTags.forEach { tag ->
                    window(tag.name, flags = AlwaysAutoResize) {
                        ModuleRegistry.modules
                            .filter { it.tag == tag }
                            .forEach { with(ModuleEntry(it)) { buildLayout() } }

                        // ToDo: Add a proper context menu to the window
                        popupContextWindow("lambda_window_ctx") {
                            text("Window Menu")
                            separator()
                            menuItem("Close This Window") {
                                // ToDo (Close under-cursor window):
                                //  - Requires a mapping from ImGui window to your visibility flag.
                                //  - Can be implemented if you track per-window IDs & vis flags.
                            }
                        }
                    }
                }

                buildMenuBar()

                ImGui.showDemoWindow()
            }
        }
    }
}
