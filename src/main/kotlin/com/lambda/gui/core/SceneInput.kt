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

package com.lambda.gui.core

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.scene.ComposeScene
import com.lambda.gui.component.KeyboardFocus
import org.lwjgl.glfw.GLFW
import java.awt.Component
import java.awt.event.KeyEvent as AwtKeyEvent

internal object Press {
	var id = 0L
		private set

	fun begin() {
		id++
	}
}

@OptIn(InternalComposeUiApi::class)
internal class SceneInput(private val scene: ComposeScene) {
	private var position = Offset.Zero
	private var pressedButtons = 0

	fun move(position: Offset) {
		this.position = position
		pointer(PointerEventType.Move)
	}

	fun button(button: Int, pressed: Boolean, modifiers: Int) {
		val mask = 1 shl button
		pressedButtons =
			if (pressed) pressedButtons or mask
			else pressedButtons and mask.inv()
		if (pressed) {
			Press.begin()
			KeyboardFocus.visible = false
		}
		pointer(
			if (pressed) PointerEventType.Press else PointerEventType.Release,
			button.toPointerButton(),
			modifiers
		)
	}

	fun scroll(horizontal: Double, vertical: Double) {
		pointer(
			PointerEventType.Scroll,
			scroll = Offset(horizontal.toFloat(), -vertical.toFloat()) * LINES_PER_NOTCH
		)
	}

	fun key(key: Int, pressed: Boolean, modifiers: Int): Boolean {
		if (pressed) Press.begin()
		val shift = modifiers and GLFW.GLFW_MOD_SHIFT != 0
		val direction =
			when (key.takeIf { pressed }) {
				GLFW.GLFW_KEY_TAB ->
					if (shift) FocusDirection.Previous
					else FocusDirection.Next
				GLFW.GLFW_KEY_UP -> FocusDirection.Up
				GLFW.GLFW_KEY_DOWN -> FocusDirection.Down
				GLFW.GLFW_KEY_LEFT -> FocusDirection.Left
				GLFW.GLFW_KEY_RIGHT -> FocusDirection.Right
				else -> null
			}
		if (direction != null) KeyboardFocus.visible = true

		val handled =
			scene.sendKeyEvent(
				KeyEvent(
					key = Key(key.toAwtKeyCode()),
					type = if (pressed) KeyEventType.KeyDown else KeyEventType.KeyUp,
					isCtrlPressed = modifiers and GLFW.GLFW_MOD_CONTROL != 0,
					isMetaPressed = modifiers and GLFW.GLFW_MOD_SUPER != 0,
					isAltPressed = modifiers and GLFW.GLFW_MOD_ALT != 0,
					isShiftPressed = shift,
				)
			)
		if (handled || direction == null) return handled
		val inOrder =
			when (direction) {
				FocusDirection.Up, FocusDirection.Left -> FocusDirection.Previous
				else -> FocusDirection.Next
			}
		return KeyboardFocus.move(direction) || direction != inOrder && KeyboardFocus.move(inOrder)
	}

	fun type(codePoint: Int): Boolean =
		Character.toChars(codePoint).fold(false) { handled, char ->
			val typed =
				AwtKeyEvent(
					awtSource,
					AwtKeyEvent.KEY_TYPED,
					System.currentTimeMillis(),
					0,
					AwtKeyEvent.VK_UNDEFINED,
					char
				)
			scene.sendKeyEvent(
				KeyEvent(
					key = Key.Unknown,
					type = KeyEventType.Unknown,
					codePoint = char.code,
					nativeEvent = typed
				)
			) || handled
		}

	fun release() {
		pressedButtons = 0
		position = OFF_SCREEN
		pointer(PointerEventType.Exit)
		scene.cancelPointerInput()
		scene.focusManager.releaseFocus()
		KeyboardFocus.visible = false
	}

	private fun pointer(
		type: PointerEventType,
		button: PointerButton? = null,
		modifiers: Int = 0,
		scroll: Offset = Offset.Zero,
	) {
		scene.sendPointerEvent(
			eventType = type,
			position = position,
			scrollDelta = scroll,
			buttons = PointerButtons(
				isPrimaryPressed = pressedButtons and 0b1 != 0,
				isSecondaryPressed = pressedButtons and 0b10 != 0,
				isTertiaryPressed = pressedButtons and 0b100 != 0,
				isBackPressed = pressedButtons and 0b1000 != 0,
				isForwardPressed = pressedButtons and 0b10000 != 0,
			),
			keyboardModifiers = PointerKeyboardModifiers(
				isCtrlPressed = modifiers and GLFW.GLFW_MOD_CONTROL != 0,
				isMetaPressed = modifiers and GLFW.GLFW_MOD_SUPER != 0,
				isAltPressed = modifiers and GLFW.GLFW_MOD_ALT != 0,
				isShiftPressed = modifiers and GLFW.GLFW_MOD_SHIFT != 0,
			),
			button = button,
		)
	}

	private companion object {
		val OFF_SCREEN = Offset(-1e6f, -1e6f)

		const val LINES_PER_NOTCH = 3f

		val awtSource = object : Component() {}

		fun Int.toPointerButton() =
			when (this) {
				GLFW.GLFW_MOUSE_BUTTON_LEFT -> PointerButton.Primary
				GLFW.GLFW_MOUSE_BUTTON_RIGHT -> PointerButton.Secondary
				GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> PointerButton.Tertiary
				GLFW.GLFW_MOUSE_BUTTON_4 -> PointerButton.Back
				GLFW.GLFW_MOUSE_BUTTON_5 -> PointerButton.Forward
				else -> null
			}

		fun Int.toAwtKeyCode() =
			when (this) {
				GLFW.GLFW_KEY_ESCAPE -> AwtKeyEvent.VK_ESCAPE
				GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> AwtKeyEvent.VK_ENTER
				GLFW.GLFW_KEY_TAB -> AwtKeyEvent.VK_TAB
				GLFW.GLFW_KEY_SPACE -> AwtKeyEvent.VK_SPACE
				GLFW.GLFW_KEY_BACKSPACE -> AwtKeyEvent.VK_BACK_SPACE
				GLFW.GLFW_KEY_INSERT -> AwtKeyEvent.VK_INSERT
				GLFW.GLFW_KEY_DELETE -> AwtKeyEvent.VK_DELETE
				GLFW.GLFW_KEY_RIGHT -> AwtKeyEvent.VK_RIGHT
				GLFW.GLFW_KEY_LEFT -> AwtKeyEvent.VK_LEFT
				GLFW.GLFW_KEY_DOWN -> AwtKeyEvent.VK_DOWN
				GLFW.GLFW_KEY_UP -> AwtKeyEvent.VK_UP
				GLFW.GLFW_KEY_PAGE_UP -> AwtKeyEvent.VK_PAGE_UP
				GLFW.GLFW_KEY_PAGE_DOWN -> AwtKeyEvent.VK_PAGE_DOWN
				GLFW.GLFW_KEY_HOME -> AwtKeyEvent.VK_HOME
				GLFW.GLFW_KEY_END -> AwtKeyEvent.VK_END
				GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT ->
					AwtKeyEvent.VK_SHIFT
				GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL ->
					AwtKeyEvent.VK_CONTROL
				GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> AwtKeyEvent.VK_ALT
				GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> AwtKeyEvent.VK_META
				in GLFW.GLFW_KEY_A..GLFW.GLFW_KEY_Z,
				in GLFW.GLFW_KEY_0..GLFW.GLFW_KEY_9 -> this
				in GLFW.GLFW_KEY_F1..GLFW.GLFW_KEY_F12 ->
					AwtKeyEvent.VK_F1 + (this - GLFW.GLFW_KEY_F1)
				else -> AwtKeyEvent.VK_UNDEFINED
			}
	}
}
