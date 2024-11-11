/*
 * Copyright 2024 Lambda
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

package com.lambda.gui.impl.clickgui.windows

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.ListWindow
import com.lambda.gui.impl.AbstractClickGui
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
            val modules = getModuleList().filter((gui as AbstractClickGui).moduleFilter)

            // Add missing module buttons
            modules.filter { module ->
                children.all { button ->
                    button.module != module
                }
            }.map { ModuleButton(it, contentComponents) }
                .forEach(contentComponents.children::add)

            // Remove deleted modules
            children.removeIf {
                it.module !in modules
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
