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

package com.lambda.gui

import com.lambda.module.ModuleRegistry
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.tag.ModuleTag
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags.AlwaysAutoResize
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text


object LambdaScreen : Screen(Text.of("Lambda GUI")) {
    override fun shouldPause() = false
    override fun removed() = ClickGui.disable()
    override fun render(context: DrawContext?, mouseX: Int, mouseY: Int, deltaTicks: Float) {}

    fun render() = DearImGui.render {
        ModuleTag.defaults
            .forEach { tag ->
                window(tag.name, flags = AlwaysAutoResize) {
                    ModuleRegistry.modules
                        .filter { it.tag == tag }
                        .forEach { with(it) { buildLayout() } }
                }
            }

        // ToDo
        mainMenuBar {
            menu("HUD") {
                menuItem("ClickGUI", "Ctrl+Alt+C") {}
            }
        }
        ImGui.showDemoWindow()
//        ImGui.showFontSelector("Font")
    }
}
