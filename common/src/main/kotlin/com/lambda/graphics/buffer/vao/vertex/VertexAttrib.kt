package com.lambda.graphics.buffer.vao.vertex

import com.lambda.graphics.gl.GLObject
import org.lwjgl.opengl.GL11C.GL_FLOAT
import org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE

enum class VertexAttrib(val componentCount: Int, componentSize: Int, val normalized: Boolean, override val gl: Int) : GLObject {
    Float(1, 4, false, GL_FLOAT),
    Vec2(2, 4, false, GL_FLOAT),
    Vec3(3, 4, false, GL_FLOAT),
    Color(4, 1, true, GL_UNSIGNED_BYTE);

    val size = componentCount * componentSize

    enum class Group(vararg val attributes: VertexAttrib) {
        POS_UV(Vec2, Vec2),

        // GUI
        FONT(Vec3, Vec2, Color), // pos, uv, color
        RECT_FILLED(Vec2, Vec2, Vec2, Vec2, Vec2, Float, Color), // pos, uv, size, roundL, roundR, shade, color
        RECT_OUTLINE(Vec2, Float, Float, Color), // pos, alpha, shade, color

        // WORLD
        DYNAMIC_RENDERER(Vec3, Vec3, Color), // prev pos, pos, color
        STATIC_RENDERER(Vec3, Color), // pos, color

        PARTICLE(Vec3, Vec2, Color); // pos, uv, color

        val stride = attributes.sumOf { attribute -> attribute.size }
    }
}