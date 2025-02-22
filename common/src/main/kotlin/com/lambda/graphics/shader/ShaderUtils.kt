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

package com.lambda.graphics.shader

import com.google.common.collect.ImmutableList
import com.lambda.util.LambdaResource
import com.lambda.util.stream
import com.mojang.blaze3d.platform.GlStateManager
import org.apache.commons.io.IOUtils
import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL30C.*

object ShaderUtils {
    private val matrixBuffer = BufferUtils.createFloatBuffer(4 * 4)
    private const val shaderInfoLogLength = 512

    /**
     * Loads and compiles an OpenGL shader using the source from the specified resource.
     *
     * This function creates a shader object based on the provided shader type, reads the shader source code from
     * the given LambdaResource using UTF-8 encoding, and attaches and compiles the shader source. If compilation fails,
     * a RuntimeException is thrown with detailed error information.
     *
     * @param type The shader type defining the OpenGL shader to create.
     * @param resource The LambdaResource containing the shader source code.
     * @return The OpenGL handle for the successfully compiled shader.
     * @throws RuntimeException If the shader compilation fails.
     */
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
                .append("Path: $resource").appendLine()
                .append("Compiler output:").appendLine()
                .append(err)

            throw RuntimeException(builder.toString())
        }

        return shader
    }

    /**
     * Creates a new OpenGL shader program by linking the provided shader objects.
     *
     * This function creates a new shader program, attaches all supplied shader IDs,
     * and attempts to link them together. If linking fails, it throws a RuntimeException
     * containing detailed error information. On successful linking, all shader objects 
     * are deleted.
     *
     * @param shaders one or more shader IDs to be linked into the program.
     * @return the ID of the linked shader program.
     * @throws RuntimeException if the shader program fails to link.
     */
    fun createShaderProgram(vararg shaders: Int): Int {
        // Create new shader program
        val program = glCreateProgram()
        val error = linkProgram(program, shaders)

        // Handle error
        error?.let { err ->
            val builder = StringBuilder()
                .append("Failed to link shader program").appendLine()
                .append("Output:").appendLine()
                .append(err)

            throw RuntimeException(builder.toString())
        }

        shaders.forEach(::glDeleteShader)

        return program
    }

    /**
     * Compiles the specified OpenGL shader.
     *
     * This function compiles the shader identified by the given ID and checks its compilation status.
     * It returns null when compilation is successful; if the compilation fails, it returns the shader's
     * error log (limited to a predefined length).
     *
     * @param shader the ID of the shader to compile.
     * @return null if the shader compiles successfully, or a string containing the error log if compilation fails.
     */
    private fun compileShader(shader: Int): String? {
        glCompileShader(shader)
        val status = glGetShaderi(shader, GL_COMPILE_STATUS)

        return if (status != GL_FALSE) null
        else glGetShaderInfoLog(shader, shaderInfoLogLength)
    }

    /**
     * Attaches the specified shaders to the shader program and links it.
     *
     * This function iterates over the provided shader IDs and attaches each to the given program.
     * It then links the program and checks the linking status. If linking is successful, it returns null;
     * otherwise, it returns the error log from the linking process.
     *
     * @param program the OpenGL shader program identifier.
     * @param shaders an array of shader identifiers to attach and link.
     * @return null if linking succeeds; otherwise, the linking error log.
     */
    private fun linkProgram(program: Int, shaders: IntArray): String? {
        shaders.forEach {
            glAttachShader(program, it)
        }

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
