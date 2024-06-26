package com.lambda.graphics.buffer.vao.vertex

import com.lambda.graphics.gl.GLObject
import org.lwjgl.opengl.GL30C.GL_DYNAMIC_DRAW
import org.lwjgl.opengl.GL30C.GL_STATIC_DRAW

enum class BufferUsage(override val gl: Int) : GLObject {
    STATIC(GL_STATIC_DRAW),
    DYNAMIC(GL_DYNAMIC_DRAW)
}