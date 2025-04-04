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

import com.lambda.graphics.RenderMain
import com.lambda.graphics.shader.ShaderUtils.createShaderProgram
import com.lambda.graphics.shader.ShaderUtils.loadShader
import com.lambda.graphics.shader.ShaderUtils.uniformMatrix
import com.lambda.util.math.Vec2d
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4f
import org.lwjgl.opengl.GL20C.*
import java.awt.Color

class Shader private constructor(name: String) {
    private val uniformCache: Object2IntMap<String> = Object2IntOpenHashMap()

    private val id: Int

    init {
        val texts = buildShaderSource(name)

        id = createShaderProgram(
            loadShader(ShaderType.VERTEX_SHADER, texts.first),
            loadShader(ShaderType.FRAGMENT_SHADER, texts.second)
        )
    }

    fun use() {
        glUseProgram(id)
        set("u_ProjModel", RenderMain.projModel)
    }

    private fun loc(name: String) =
        uniformCache.getOrPut(name) {
            glGetUniformLocation(id, name)
        }

    operator fun set(name: String, v: Boolean) =
        glUniform1i(loc(name), if (v) 1 else 0)

    operator fun set(name: String, v: Int) =
        glUniform1i(loc(name), v)

    operator fun set(name: String, v: Double) =
        glUniform1f(loc(name), v.toFloat())

    operator fun set(name: String, vec: Vec2d) =
        glUniform2f(loc(name), vec.x.toFloat(), vec.y.toFloat())

    operator fun set(name: String, vec: Vec3d) =
        glUniform3f(loc(name), vec.x.toFloat(), vec.y.toFloat(), vec.z.toFloat())

    operator fun set(name: String, color: Color) =
        glUniform4f(
            loc(name),
            color.red / 255f,
            color.green / 255f,
            color.blue / 255f,
            color.alpha / 255f
        )

    operator fun set(name: String, mat: Matrix4f) =
        uniformMatrix(loc(name), mat)

    companion object {
        private val shaderCache = Object2ObjectOpenHashMap<String, Shader>()

        fun shader(path: String) =
            shaderCache.getOrPut(path) {
                Shader(path)
            }
    }
}
