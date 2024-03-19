package com.lambda.graphics.buffer.vao.vertex

import com.lambda.graphics.gl.GLObject
import org.lwjgl.opengl.GL11C.*

enum class VertexAttrib(val componentCount: Int, componentSize: Int, val normalized: Boolean, override val gl: Int) : GLObject {
    Vec2(2, 4, false, GL_FLOAT),
    Vec3(3, 4, false, GL_FLOAT),
    Color(4, 1, true, GL_UNSIGNED_BYTE);

    val size = componentCount * componentSize

    enum class Group(vararg val attributes: VertexAttrib) {
        POS2_COLOR(Vec2, Color),

        RECT(Vec2, Vec2, Vec3, Color);

        val stride = attributes.sumOf { attribute -> attribute.size }
    }
}