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

import com.lambda.Lambda.mc
import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.GLFW_ARROW_CURSOR
import org.lwjgl.glfw.GLFW.GLFW_POINTING_HAND_CURSOR
import org.lwjgl.glfw.GLFW.GLFW_RESIZE_EW_CURSOR
import org.lwjgl.glfw.GLFW.GLFW_RESIZE_NS_CURSOR
import org.lwjgl.glfw.GLFW.GLFW_RESIZE_NWSE_CURSOR
import org.lwjgl.glfw.GLFW.glfwCreateStandardCursor
import org.lwjgl.glfw.GLFW.glfwSetCursor
import kotlin.jvm.Throws

class Mouse {
    enum class Button(val key: Int) {
        Left(GLFW.GLFW_MOUSE_BUTTON_LEFT),
        Right(GLFW.GLFW_MOUSE_BUTTON_RIGHT),
        Middle(GLFW.GLFW_MOUSE_BUTTON_MIDDLE),
        Button4(GLFW.GLFW_MOUSE_BUTTON_4),
        Button5(GLFW.GLFW_MOUSE_BUTTON_5),
        Button6(GLFW.GLFW_MOUSE_BUTTON_6),
        Button7(GLFW.GLFW_MOUSE_BUTTON_7),
        Button8(GLFW.GLFW_MOUSE_BUTTON_8);

        val isMainButton get() = key == GLFW.GLFW_MOUSE_BUTTON_LEFT || key == GLFW.GLFW_MOUSE_BUTTON_RIGHT

        companion object {
            private val mouseCodeMap = entries.associateBy { it.key }
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
        Click,
        Release;

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

    enum class Cursor(private val getCursorPointer: () -> Long) {
        Arrow(::arrow),
        Pointer(::pointer),
        ResizeH(::resizeH), ResizeV(::resizeV), ResizeHV(::resizeHV);

        fun set() {
            if (lastCursor == this) return
            lastCursor = this

            RenderSystem.assertOnRenderThread()
            glfwSetCursor(mc.window.handle, getCursorPointer())
        }
    }

    class CursorController {
        private var lastSetCursor: Cursor? = null

        fun setCursor(cursor: Cursor) {
            // We're doing this to let other controllers be able to set the cursor when this one doesn't change
            if (lastSetCursor == cursor && cursor == Cursor.Arrow) return

            cursor.set()
            lastSetCursor = cursor
        }

        fun reset() = setCursor(Cursor.Arrow)
    }

    companion object {
        private val arrow by lazy { glfwCreateStandardCursor(GLFW_ARROW_CURSOR) }
        private val pointer by lazy { glfwCreateStandardCursor(GLFW_POINTING_HAND_CURSOR) }
        private val resizeH by lazy { glfwCreateStandardCursor(GLFW_RESIZE_EW_CURSOR) }
        private val resizeV by lazy { glfwCreateStandardCursor(GLFW_RESIZE_NS_CURSOR) }
        private val resizeHV by lazy { glfwCreateStandardCursor(GLFW_RESIZE_NWSE_CURSOR) }
        var lastCursor = Cursor.Arrow
    }
}
