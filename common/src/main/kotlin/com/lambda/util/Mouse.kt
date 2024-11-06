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
