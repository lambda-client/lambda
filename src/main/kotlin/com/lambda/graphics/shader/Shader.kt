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

import com.lambda.Lambda.mc
import com.lambda.graphics.RenderMain
import com.lambda.graphics.shader.ShaderUtils.createShaderProgram
import com.lambda.graphics.shader.ShaderUtils.loadShader
import com.lambda.graphics.shader.ShaderUtils.uniformMatrix
import com.lambda.util.LambdaResource
import com.lambda.util.math.Vec2d
import com.lambda.util.stream
import com.lambda.util.text
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4f
import org.lwjgl.opengl.GL20C.glGetUniformLocation
import org.lwjgl.opengl.GL20C.glUniform1f
import org.lwjgl.opengl.GL20C.glUniform1i
import org.lwjgl.opengl.GL20C.glUniform2f
import org.lwjgl.opengl.GL20C.glUniform3f
import org.lwjgl.opengl.GL20C.glUniform4f
import org.lwjgl.opengl.GL20C.glUseProgram
import java.awt.Color

class Shader(vertex: LambdaResource, fragment: LambdaResource) {
    private val uniformCache: Object2IntMap<String> = Object2IntOpenHashMap()

    private val id: Int = createShaderProgram(
        loadShader(ShaderType.VertexShader, vertex.text),
        loadShader(ShaderType.FragmentShader, fragment.text)
    )

	fun use() {
        glUseProgram(id)
        set("u_ProjModel", RenderMain.projModel)

        val x = mc.gameRenderer.camera.pos.x.toFloat()
        val y = mc.gameRenderer.camera.pos.y.toFloat()
        val z = mc.gameRenderer.camera.pos.z.toFloat()

        val view = Matrix4f()
            .translation(-x, -y, -z)

        set("u_View", view)
    }

    private fun loc(name: String) =
        if (uniformCache.containsKey(name))
            uniformCache.getInt(name)
        else
            glGetUniformLocation(id, name).let { location ->
                uniformCache.put(name, location)
                location
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
}
