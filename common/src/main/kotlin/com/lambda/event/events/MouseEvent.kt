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

package com.lambda.event.events

import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d

sealed class MouseEvent {
    /**
     * Represents a mouse click event
     *
     * @property button The button that was clicked
     * @property action The action performed (e.g., press or release)
     * @property modifiers An integer representing any modifiers (e.g., shift or ctrl) active during the event
     * @property position The x and y position of the mouse on the screen
     */
    data class Click(
        val button: Mouse.Button,
        val action: Mouse.Action,
        val modifiers: Int,
        val position: Vec2d,
    ) : ICancellable by Cancellable() {
        constructor(button: Int, action: Int, modifiers: Int, position: Vec2d) : this(
            Mouse.Button.fromMouseCode(button),
            Mouse.Action.fromActionCode(action),
            modifiers,
            position
        )
    }

    /**
     * Represents a mouse scroll event
     *
     * @property delta The amount of scrolling in the x and y directions
     */
    data class Scroll(
        val delta: Vec2d,
    ) : ICancellable by Cancellable()

    /**
     * Represents a mouse move event.
     *
     * @property position The x and y position of the mouse on the screen.
     */
    data class Move(
        val position: Vec2d,
    ) : ICancellable by Cancellable()
}
