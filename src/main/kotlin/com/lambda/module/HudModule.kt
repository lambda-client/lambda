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
) : Module(name, description, tag, alwaysListening, enabledByDefault, defaultKeybind = defaultKeybind) {
    val backgroundColor = setting("Background Color", Color(0, 0, 0, 0))

    /**
     * Where the element sits, as the fraction of the room it has to move in: 0 puts it against
     * the left or top edge of the screen and 1 against the right or bottom. A fraction keeps an
     * element docked to its edge when the window changes size. Set by dragging it in the HUD editor.
     */
    var anchorX by setting("HUD X", DEFAULT_ANCHOR, 0f..1f, 0.001f) { false }
    var anchorY by setting("HUD Y", (DEFAULT_ANCHOR + DEFAULT_SPACING * created++).coerceAtMost(1f), 0f..1f, 0.001f) { false }

    /**
     * The element as it is drawn. Read whatever it shows through
     * [com.lambda.gui.core.observe], so it is redrawn when that changes and only then.
     */
    @Composable
    abstract fun Content()

    private companion object {
        const val DEFAULT_ANCHOR = 0.01f

        /** Elements start out one under the other down the left edge, rather than in a pile */
        const val DEFAULT_SPACING = 0.06f
        var created = 0
    }
}
