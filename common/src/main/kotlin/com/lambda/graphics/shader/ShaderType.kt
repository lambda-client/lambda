package com.lambda.graphics.shader

import com.lambda.graphics.gl.GLObject
import org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER
import org.lwjgl.opengl.GL20C.GL_VERTEX_SHADER

enum class ShaderType(override val gl: Int) : GLObject {
    FRAGMENT_SHADER(GL_FRAGMENT_SHADER),
    VERTEX_SHADER(GL_VERTEX_SHADER)
}