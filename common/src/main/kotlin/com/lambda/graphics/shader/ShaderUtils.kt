package com.lambda.graphics.shader

import com.google.common.collect.ImmutableList
import com.lambda.util.LambdaResource
import com.mojang.blaze3d.platform.GlStateManager
import org.apache.commons.io.IOUtils
import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL30C.*

object ShaderUtils {
    private val matrixBuffer = BufferUtils.createFloatBuffer(4 * 4)
    private const val shaderInfoLogLength = 512

    fun loadShader(type: ShaderType, resource: LambdaResource): Int {
        // Create new shader object
        val shader = glCreateShader(type.gl)

        // Attach source code and compile it
        val text = IOUtils.toString(resource.stream, Charsets.UTF_8)
        GlStateManager.glShaderSource(shader, ImmutableList.of(text))
        val error = compileShader(shader)

        // Handle error
        error?.let { err ->
            val builder = StringBuilder()
                .append("Failed to compile ${type.name} shader").appendLine()
                .append("Path: ${resource.path}").appendLine()
                .append("Compiler output:").appendLine()
                .append(err)

            throw RuntimeException(builder.toString())
        }

        return shader
    }

    fun createShaderProgram(vert: Int, frag: Int): Int {
        // Create new shader program
        val program = glCreateProgram()
        val error = linkProgram(program, vert, frag)

        // Handle error
        error?.let { err ->
            val builder = StringBuilder()
                .append("Failed to link shader program").appendLine()
                .append("Output:").appendLine()
                .append(err)

            throw RuntimeException(builder.toString())
        }

        glDeleteShader(vert)
        glDeleteShader(frag)

        return program
    }

    private fun compileShader(shader: Int): String? {
        glCompileShader(shader)
        val status = glGetShaderi(shader, GL_COMPILE_STATUS)

        return if (status != GL_FALSE) null
        else glGetShaderInfoLog(shader, shaderInfoLogLength)
    }

    private fun linkProgram(program: Int, vertShader: Int, fragShader: Int): String? {
        glAttachShader(program, vertShader)
        glAttachShader(program, fragShader)
        glLinkProgram(program)

        val status = glGetProgrami(program, GL_LINK_STATUS)

        return if (status != GL_FALSE) null
        else glGetProgramInfoLog(program, shaderInfoLogLength)
    }

    fun uniformMatrix(location: Int, v: Matrix4f) {
        v.get(matrixBuffer)
        glUniformMatrix4fv(location, false, matrixBuffer)
    }
}