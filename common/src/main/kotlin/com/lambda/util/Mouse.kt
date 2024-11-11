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

package com.lambda.util

import org.lwjgl.glfw.GLFW

class Mouse {
    @JvmInline
    value class Button(val key: Int) {
        companion object {
            val Left = Button(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            val Right = Button(GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            val Middle = Button(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
        }

        val isMainButton get() = key == GLFW.GLFW_MOUSE_BUTTON_LEFT || key == GLFW.GLFW_MOUSE_BUTTON_RIGHT
    }

    enum class Action {
        Click,
        Release
    }
}
