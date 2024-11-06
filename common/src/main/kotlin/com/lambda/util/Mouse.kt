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
import org.lwjgl.glfw.GLFW.*

class Mouse {
    @JvmInline
    value class Button(val key: Int) {
        companion object {
            val Left = Button(GLFW_MOUSE_BUTTON_LEFT)
            val Right = Button(GLFW_MOUSE_BUTTON_RIGHT)
            val Middle = Button(GLFW_MOUSE_BUTTON_MIDDLE)
        }

        val isMainButton get() = key == GLFW_MOUSE_BUTTON_LEFT || key == GLFW_MOUSE_BUTTON_RIGHT
    }

    enum class Action {
        Click,
        Release
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
