package com.lambda.graphics.buffer.vao.vertex

import com.lambda.graphics.gl.GLObject
import org.lwjgl.opengl.GL11C.*

enum class VertexMode(val indicesCount: Int, override val gl: Int) : GLObject {
    LINES(2, GL_LINES),
    TRIANGLES(3, GL_TRIANGLES)
}