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

package com.lambda.module

import com.lambda.config.settings.complex.Bind
import com.lambda.gui.Layout
import com.lambda.module.tag.ModuleTag
import java.awt.Color

abstract class HudModule(
    name: String,
    description: String = "",
    tag: ModuleTag,
    val customWindow: Boolean = false,
    alwaysListening: Boolean = false,
    enabledByDefault: Boolean = false,
    defaultKeybind: Bind = Bind.Empty,
) : Module(name, description, tag, alwaysListening, enabledByDefault, defaultKeybind), Layout {
    val backgroundColor by setting("Background Color", Color(0, 0, 0, 0))
    val outline by setting("Show Outline", false)
    val outlineWidth by setting("Outline Width", 1f, 0f..10f, 0.1f) { outline }
}
