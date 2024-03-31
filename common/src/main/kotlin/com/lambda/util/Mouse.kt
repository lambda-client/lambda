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
    }

    enum class Action {
        Click,
        Release
    }
}