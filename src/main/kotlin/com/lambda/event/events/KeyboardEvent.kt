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

import com.lambda.config.settings.complex.Bind
import com.lambda.event.Event
import com.lambda.util.KeyCode
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.GLFW_RELEASE

sealed class KeyboardEvent {
    /**
     * Represents a key press
     *
     * @property keyCode The key code of the key that was pressed
     * @property scanCode The scan code of the key that was pressed
     * @property action The action that was performed on the key (Pressed, Released)
     * @property modifiers The modifiers that were active when the key was pressed
     *
     * @see <a href="https://learn.microsoft.com/en-us/windows/win32/inputdev/about-keyboard-input#keyboard-input-model">About Keyboards</a>
     */
    data class Press(
        val keyCode: Int,
        val scanCode: Int,
        val action: Int,
        val modifiers: Int,
    ) : Event {
        /**
         * Maps the scancode to the US layout
         */
        val translated: KeyCode
            get() = KeyCode.virtualMapUS(keyCode, scanCode)

        val isPressed = action >= GLFW_PRESS
        val isReleased = action == GLFW_RELEASE

        fun satisfies(bind: Bind) = bind.key == keyCode && bind.modifiers and modifiers == bind.modifiers
    }

    /**
     * Represents glfwSetCharCallback events
     *
     * Keys and characters do not map 1:1.
     * A single key press may produce several characters, and a single
     * character may require several keys to produce
     */
    data class Char(val char: kotlin.Char) : Event
}
