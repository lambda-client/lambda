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

package com.lambda.module

import androidx.compose.runtime.Composable
import com.lambda.config.settings.complex.Bind
import java.awt.Color

abstract class HudModule(
    name: String,
    description: String = "",
    tag: ModuleTag,
    alwaysListening: Boolean = false,
    enabledByDefault: Boolean = false,
    defaultKeybind: Bind = Bind.EMPTY,
) : Module(
    name,
    description,
    tag,
    alwaysListening,
    enabledByDefault,
    defaultKeybind = defaultKeybind
) {
    val backgroundColor = setting("Background Color", Color(0, 0, 0, 0))

    var anchorX by setting("HUD X", DEFAULT_ANCHOR, 0f..1f, 0.001f) { false }
    var anchorY by setting("HUD Y", (DEFAULT_ANCHOR + DEFAULT_SPACING * created++).coerceAtMost(1f), 0f..1f, 0.001f) { false }

    @Composable
    abstract fun Content()

    private companion object {
        const val DEFAULT_ANCHOR = 0.01f

        const val DEFAULT_SPACING = 0.06f
        var created = 0
    }
}
