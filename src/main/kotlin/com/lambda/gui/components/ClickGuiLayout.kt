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

import com.lambda.config.Configuration
import com.lambda.core.Loadable
import com.lambda.event.events.GuiEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.gui.dsl.ImGuiBuilder.buildLayout
import com.lambda.module.ModuleRegistry
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.Communication.info
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags.AlwaysAutoResize

object ClickGuiLayout : Loadable {
    init {
        listen<GuiEvent.NewFrame> {
            if (!ClickGui.isEnabled) return@listen

            buildLayout {
                ModuleTag.defaults
                    .forEach { tag ->
                        window(tag.name, flags = AlwaysAutoResize) {
                            ModuleRegistry.modules
                                .filter { it.tag == tag }
                                .forEach { with(ModuleEntry(it)) { buildLayout() } }
                        }
                    }

                mainMenuBar {
                    menu("File") {
                        menuItem("Save Configs", "Ctrl+S") {
                            Configuration.configurations.forEach { config ->
                                config.trySave(true)
                            }
                            runSafe {
                                info("Saved ${Configuration.configurations.size} configuration files.")
                            }
                        }
                        menuItem("Load Configs", "Ctrl+L") {
                            Configuration.configurations.forEach { config ->
                                config.tryLoad()
                            }
                            runSafe {
                                info("Loaded ${Configuration.configurations.size} configuration files.")
                            }
                        }
                    }
                }

                ImGui.showDemoWindow()
            }
        }
    }
}
