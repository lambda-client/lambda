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

package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d

sealed class GuiEvent : Event {
    data object Show : GuiEvent()
    data object Hide : GuiEvent()
    data object Tick : GuiEvent()
    data object Update : GuiEvent()
    data object Render : GuiEvent()

    class KeyPress(val key: KeyCode) : GuiEvent()

    class CharTyped(val char: Char) : GuiEvent()

    class MouseClick(val button: Mouse.Button, val action: Mouse.Action, val mouse: Vec2d) : GuiEvent()

    class MouseMove(val mouse: Vec2d) : GuiEvent()

    class MouseScroll(val mouse: Vec2d, val delta: Double) : GuiEvent()
}
