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

import org.lwjgl.glfw.GLFW

class Mouse {
    enum class Button {
        Left,
        Right,
        Middle,
        Button4,
        Button5,
        Button6,
        Button7,
        Button8;

        val isMainButton get() = ordinal == GLFW.GLFW_MOUSE_BUTTON_LEFT || ordinal == GLFW.GLFW_MOUSE_BUTTON_RIGHT

        companion object {
            private val mouseCodeMap = entries.associateBy { it.ordinal }
            private val nameMap = entries.associateBy { it.name.lowercase() }

            @Throws(IllegalArgumentException::class)
            fun fromMouseCode(code: Int) =
                mouseCodeMap[code] ?: throw IllegalArgumentException("Mouse code $code not found in mouseCodeMap.")

            @Throws(IllegalArgumentException::class)
            fun fromMouseName(name: String) =
                nameMap[name.lowercase()] ?: throw IllegalArgumentException("Mouse name '$name' not found in nameMap.")
        }
    }

    enum class Action {
        Release,
        Click;

        companion object {
            private val mouseActionMap = entries.associateBy { it.ordinal }
            private val nameMap = entries.associateBy { it.name.lowercase() }

            @Throws(IllegalArgumentException::class)
            fun fromActionCode(code: Int) =
                mouseActionMap[code] ?: throw IllegalArgumentException("Action code $code not found in mouseActionMap.")

            @Throws(IllegalArgumentException::class)
            fun fromActionName(name: String) =
                nameMap[name.lowercase()] ?: throw IllegalArgumentException("Action name '$name' not found in nameMap.")
        }
    }
}
