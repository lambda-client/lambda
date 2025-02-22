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
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4f
import org.lwjgl.opengl.GL20C.*
import java.awt.Color

class Shader private constructor(fragmentPath: String, vertexPath: String) {
    private val uniformCache: Object2IntMap<String> = Object2IntOpenHashMap()

    private val id = createShaderProgram(
        loadShader(ShaderType.VERTEX_SHADER, "shaders/vertex/$vertexPath.vert"),
        loadShader(ShaderType.FRAGMENT_SHADER, "shaders/fragment/$fragmentPath.frag")
    )

    /**
     * Activates the shader program and updates the "u_ProjModel" uniform.
     *
     * This function sets the active OpenGL program to this shader and ensures that the
     * current projection model matrix from RenderMain is applied by updating the corresponding uniform.
     */
    fun use() {
        glUseProgram(id)
        set("u_ProjModel", RenderMain.projModel)
    }

    /**
             * Retrieves the location of the specified uniform variable.
             *
             * This function checks a cache for the uniform location. If the uniform location is already cached,
             * it returns the cached value. Otherwise, it queries OpenGL for the location using `glGetUniformLocation`,
             * caches the result, and then returns it.
             *
             * @param name the name of the uniform variable.
             * @return the location of the uniform variable.
             */
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

    /**
         * Assigns a 4x4 matrix value to a uniform variable in the shader program.
         *
         * This operator overload uses the uniform variable name to locate its position
         * within the shader and updates it with the provided matrix data.
         *
         * @param name the name of the uniform variable.
         * @param mat the 4x4 matrix to assign to the uniform.
         */
        operator fun set(name: String, mat: Matrix4f) =
        uniformMatrix(loc(name), mat)

    companion object {
        private val shaderCache = hashMapOf<Pair<String, String>, Shader>()

        /**
             * Retrieves or creates a shader instance using a single shader path.
             *
             * This function returns a [Shader] by applying the provided file path for both the vertex and fragment shader sources.
             *
             * @param path the file path for the shader sources used for both vertex and fragment shaders.
             * @return the corresponding [Shader] instance.
             */
            fun shader(path: String) =
            shader(path, path)

        /**
             * Retrieves a Shader instance for the specified fragment and vertex shader paths.
             *
             * This function checks the cache for an existing Shader corresponding to the given paths. If none is found,
             * it creates a new Shader instance, caches it, and returns the instance.
             *
             * @param fragmentPath The file path to the fragment shader.
             * @param vertexPath The file path to the vertex shader.
             * @return The Shader instance associated with the provided shader paths.
             */
            fun shader(fragmentPath: String, vertexPath: String) =
            shaderCache.getOrPut(fragmentPath to vertexPath) {
                Shader(fragmentPath, vertexPath)
            }
    }
}
