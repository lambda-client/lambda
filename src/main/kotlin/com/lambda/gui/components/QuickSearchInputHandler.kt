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

package com.lambda.gui.components

import com.lambda.core.Loadable
import com.lambda.event.events.KeyboardEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.util.KeyCode
import org.lwjgl.glfw.GLFW

object QuickSearchInputHandler : Loadable {
    private var lastShiftPressTime = 0L
    private var lastShiftKeyCode = -1
    private val doubleShiftTimeWindow = 500L // 500ms window for double shift

    init {
        listenUnsafe<KeyboardEvent.Press> { event ->
            handleKeyPress(event)
        }
    }

    private fun handleKeyPress(event: KeyboardEvent.Press) {
        // Check if it's a shift key press
        if (event.isPressed && (event.keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || event.keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            val currentTime = System.currentTimeMillis()
            
            // Check if this is a double shift press
            if (lastShiftKeyCode == event.keyCode && 
                currentTime - lastShiftPressTime <= doubleShiftTimeWindow) {
                // Double shift detected!
                QuickSearch.open()
                // Reset to prevent triple-shift issues
                lastShiftPressTime = 0L
                lastShiftKeyCode = -1
            } else {
                // First shift press, record it
                lastShiftPressTime = currentTime
                lastShiftKeyCode = event.keyCode
            }
        }
    }
}