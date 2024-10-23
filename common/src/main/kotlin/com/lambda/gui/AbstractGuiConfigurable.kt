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

package com.lambda.gui

import com.lambda.config.Configurable
import com.lambda.config.configurations.GuiConfig
import com.lambda.core.Loadable
import com.lambda.gui.impl.AbstractClickGui
import com.lambda.gui.impl.clickgui.windows.tag.TagWindow
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.Vec2d

abstract class AbstractGuiConfigurable(
    private val ownerGui: AbstractClickGui,
    private val tags: Set<ModuleTag>,
    override val name: String
) : Configurable(GuiConfig), Loadable {
    var mainWindows by setting("windows", defaultWindows)

    private val defaultWindows
        get() =
            tags.mapIndexed { index, tag ->
                TagWindow(tag, ownerGui).apply {
                    val step = 5.0
                    position = Vec2d((width + step) * index, 0.0) + step
                }
            }
}
