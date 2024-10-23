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

package com.lambda.gui.impl.hudgui

import com.lambda.gui.HudGuiConfigurable
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.impl.AbstractClickGui
import com.lambda.gui.impl.clickgui.LambdaClickGui
import com.lambda.module.HudModule
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d

object LambdaHudGui : AbstractClickGui("HudGui") {
    override val moduleFilter: (Module) -> Boolean = {
        it is HudModule
    }

    override val configurable get() = HudGuiConfigurable
    private val hudModules get() = ModuleRegistry.modules.filterIsInstance<HudModule>()

    private var dragInfo: Pair<Vec2d, HudModule>? = null

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        when (e) {
            is GuiEvent.Show -> {
                dragInfo = null

                setCloseTask {
                    LambdaClickGui.show()
                }
            }

            is GuiEvent.MouseMove -> {
                if (closing) dragInfo = null

                dragInfo?.let {
                    it.second.position = e.mouse - it.first
                }
            }

            is GuiEvent.MouseClick -> {
                dragInfo = null

                if (hoveredWindow == null &&
                    e.action == Mouse.Action.Click &&
                    e.button == Mouse.Button.Left
                ) hudModules.filter(Module::isEnabled).firstOrNull { e.mouse in it.rect }?.let {
                    dragInfo = e.mouse - it.position to it
                }
            }
        }
    }
}
