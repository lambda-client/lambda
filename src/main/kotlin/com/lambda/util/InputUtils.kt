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

package com.lambda.util

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.core.Loadable
import com.lambda.event.events.KeyboardEvent
import com.lambda.event.events.MouseEvent
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SUPER
import org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT
import org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL
import org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT
import org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SUPER
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_1
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_8
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.glfwGetKey
import org.lwjgl.glfw.GLFW.glfwGetMouseButton

object InputUtils : Loadable {
    private val lastPressedKeys = Int2IntArrayMap() // Keep track of the previously pressed keys to report GLFW_RELEASE states

    /**
     * Returns whether any of the key-codes (not scan-codes) are being pressed
     */
    fun SafeContext.isKeyPressed(vararg keys: Int) =
        keys.any { glfwGetKey(mc.window.handle, it) >= GLFW_PRESS }

    /**
     * Creates a new event from the currently pressed keys.
     * Note: This function is extremely expensive to execute, it is recommended to not use
     * it unless you absolutely need to. Additionally, you might screw with the key cache.
     */
    fun newKeyboardEvent(): KeyboardEvent.Press? {
        val pressedKeys = keys
            .associateWith { glfwGetKey(mc.window.handle, it) }
            .filter { (key, state) -> state >= GLFW_PRESS || lastPressedKeys[key] >= GLFW_PRESS }
            .also { lastPressedKeys.clear() }
            .onEach { (key, state) -> lastPressedKeys[key] = state }

        // FixMe: If you are pressing two or more keys considered 'modifier' keys, you must release both of them at the
        //  same time in order to receive an update stipulating that the last key (not actually a modifier) was released alongside its modifiers.
        //  For the time being, I will allow this as players can still bind unique 'modifier' keys with no issues.

        // FixMe: The order in which modifier keys are ordered is wrong. When a user presses Left Control + Left Alt, the user must
        //  press Left Alt + Left Control as the modifier key in order for the event to satisfies the bind

        val mods = pressedKeys.keys
            .filter { it in GLFW_KEY_LEFT_SHIFT..GLFW_KEY_RIGHT_SUPER && lastPressedKeys.keys.firstOrNull()?.equals(it) == false }
            .foldRight(0) { v, acc -> acc or modMap.getValue(v) }

        val key = pressedKeys
            .firstNotNullOfOrNull { (key, state) -> key to state } ?: return null

        val scancode = scancodes.getOrElse(key.first) { 0 }

        return KeyboardEvent.Press(key.first, scancode, key.second, mods)
    }

    /**
     * Creates a new mouse event from the current glfw states.
     */
    fun newMouseEvent(): MouseEvent.Click? {
        val mods = (GLFW_KEY_LEFT_SHIFT..GLFW_KEY_RIGHT_SUPER)
            .filter { glfwGetKey(mc.window.handle, it) >= GLFW_PRESS }
            .foldRight(0) { v, acc -> acc or modMap.getValue(v) }

        val mouse = mouses
            .firstOrNull { glfwGetMouseButton(mc.window.handle, it) == GLFW_PRESS }
            ?: return null

        return MouseEvent.Click(mouse, GLFW_PRESS, mods)
    }

    private val keys = KeyCode.entries.map { it.code }.filter { it > 0 }
    private val scancodes = keys.associateWith { GLFW.glfwGetKeyScancode(it) }

    private val mouses = GLFW_MOUSE_BUTTON_1..GLFW_MOUSE_BUTTON_8

    private val modMap = mapOf(
        GLFW_KEY_LEFT_SHIFT     to 0x1,
        GLFW_KEY_RIGHT_SHIFT    to 0x1,
        GLFW_KEY_LEFT_CONTROL   to 0x2,
        GLFW_KEY_RIGHT_CONTROL  to 0x2,
        GLFW_KEY_LEFT_ALT       to 0x4,
        GLFW_KEY_RIGHT_ALT      to 0x4,
        GLFW_KEY_LEFT_SUPER     to 0x8,
        GLFW_KEY_RIGHT_SUPER    to 0x8,
    )
}
