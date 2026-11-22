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

package com.lambda.gui.clickgui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.lambda.gui.GuiConfig
import com.lambda.gui.settings.PanelPage
import com.lambda.module.Module

internal class SettingsPanelState(val module: Module) {
    var page by mutableStateOf(PanelPage.Settings)
    var extension by mutableStateOf(0.dp)
    var row by mutableStateOf(Rect.Zero)
    var settled by mutableStateOf(false)
}

@Stable
internal class OpenSettings {
    private val states = HashMap<Module, SettingsPanelState>()
    private val open = mutableStateListOf<Module>()
    private val closing = mutableStateListOf<Module>()

    val panels: List<SettingsPanelState> get() = (open + closing).map(::stateOf)

    fun stateOf(module: Module) = states.getOrPut(module) { SettingsPanelState(module) }

    fun panelOf(module: Module) =
        if (module in open || module in closing) stateOf(module)
        else null

    fun isOpen(module: Module) = module in open

    fun open(module: Module) {
        if (!GuiConfig.multipleSettings) closeAll()
        closing -= module
        if (module !in open) open += module
    }

    fun toggle(module: Module) =
        if (isOpen(module)) close(module)
        else open(module)

    fun close(module: Module) {
        if (open.remove(module)) closing += module
    }

    fun close(modules: Collection<Module>) =
        open.filter { it in modules }.forEach(::close)

    fun closeAll() = open.toList().forEach(::close)

    fun closed(module: Module) {
        closing -= module
    }

    fun forget() {
        open.clear()
        closing.clear()
    }
}
