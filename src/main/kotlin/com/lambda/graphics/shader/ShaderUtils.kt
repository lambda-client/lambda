/*
 * Copyright 2025 Lambda
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

import com.mojang.blaze3d.opengl.GlStateManager
import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL30C.GL_COMPILE_STATUS
import org.lwjgl.opengl.GL30C.GL_FALSE
import org.lwjgl.opengl.GL30C.GL_LINK_STATUS
import org.lwjgl.opengl.GL30C.glAttachShader
import org.lwjgl.opengl.GL30C.glCompileShader
import org.lwjgl.opengl.GL30C.glCreateProgram
import org.lwjgl.opengl.GL30C.glCreateShader
import org.lwjgl.opengl.GL30C.glDeleteShader
import org.lwjgl.opengl.GL30C.glGetProgramInfoLog
import org.lwjgl.opengl.GL30C.glGetProgrami
import org.lwjgl.opengl.GL30C.glGetShaderInfoLog
import org.lwjgl.opengl.GL30C.glGetShaderi
import org.lwjgl.opengl.GL30C.glLinkProgram
import org.lwjgl.opengl.GL30C.glUniformMatrix4fv

object ShaderUtils {
	private val matrixBuffer = BufferUtils.createFloatBuffer(4 * 4)
	private const val shaderInfoLogLength = 512

	fun loadShader(type: ShaderType, text: String): Int {
		// Create new shader object
		val shader = glCreateShader(type.gl)

		// Attach source code and compile it
		GlStateManager.glShaderSource(shader, text)
		val error = compileShader(shader)

		// Handle error
		error?.let { err ->
			val builder = StringBuilder()
				.append("Failed to compile ${type.name} shader").appendLine()
				.append("Compiler output:").appendLine()
				.append(err)
				.appendLine().appendLine("CODE:")
				.append(text)

			throw RuntimeException(builder.toString())
		}

		return shader
	}

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

	private fun compileShader(shader: Int): String? {
		glCompileShader(shader)
		val status = glGetShaderi(shader, GL_COMPILE_STATUS)

		return if (status != GL_FALSE) null
		else glGetShaderInfoLog(shader, shaderInfoLogLength)
	}

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
