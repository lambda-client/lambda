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
import com.lambda.gui.Layout
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.module.HudModule
import com.lambda.module.ModuleRegistry
import imgui.flag.ImGuiWindowFlags

object HudGuiLayout : Loadable, Layout {
    const val DEFAULT_HUD_FLAGS =
        ImGuiWindowFlags.NoDecoration or
                ImGuiWindowFlags.NoBackground or
                ImGuiWindowFlags.AlwaysAutoResize

    override fun invoke(p1: ImGuiBuilder) = with(p1) {
        ModuleRegistry.modules
            .filterIsInstance<HudModule>()
            .filter { it.isEnabled }
            .forEach {
                window("##${it.name}", flags = DEFAULT_HUD_FLAGS) { it.element(this) }
            }
    }

    init {
        listen<GuiEvent.NewFrame> { invoke(ImGuiBuilder) }
    }
}
