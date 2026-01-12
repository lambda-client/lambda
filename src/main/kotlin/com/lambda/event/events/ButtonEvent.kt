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

package com.lambda.event.events

import com.lambda.config.settings.complex.Bind
import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import com.lambda.util.KeyCode
import com.lambda.util.math.Vec2d
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.GLFW_RELEASE
import org.lwjgl.glfw.GLFW.GLFW_REPEAT

sealed class ButtonEvent : ICancellable by Cancellable() {
	abstract val action: Int
	abstract val modifiers: Int

	val isPressed get() = action >= GLFW_PRESS
	val isReleased get() = action == GLFW_RELEASE
	val isRepeated get() = action == GLFW_REPEAT

	abstract fun satisfies(bind: Bind): Boolean

	sealed class Mouse {
		/**
		 * Represents a mouse click event
		 *
		 * @property button The button that was clicked
		 * @property action The action performed (e.g., press or release)
		 * @property modifiers An integer representing any modifiers (e.g., shift or ctrl) active during the event
		 */
		data class Click(
			val button: Int,
			override val action: Int,
			override val modifiers: Int,
		) : ButtonEvent() {
			override fun satisfies(bind: Bind) = bind.modifiers and modifiers == bind.modifiers && bind.mouse == button
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

	sealed class Keyboard {
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
			override val action: Int,
			override val modifiers: Int,
		) : ButtonEvent() {
			val bind: Bind
				get() = Bind(translated.code, modifiers, -1)

			val translated: KeyCode
				get() = KeyCode.virtualMapUS(keyCode, scanCode)

			override fun satisfies(bind: Bind) = bind.key == translated.code && bind.modifiers and modifiers == bind.modifiers
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
}