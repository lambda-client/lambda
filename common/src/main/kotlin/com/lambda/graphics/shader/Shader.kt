package com.lambda.graphics.shader

import com.lambda.graphics.RenderMain
import com.lambda.graphics.shader.ShaderUtils.createShaderProgram
import com.lambda.graphics.shader.ShaderUtils.loadShader
import com.lambda.graphics.shader.ShaderUtils.uniformMatrix
import com.lambda.threading.mainThread
import com.lambda.util.LambdaResource
import com.lambda.util.math.Vec2d
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4f
import org.lwjgl.opengl.GL20C.*
import java.awt.Color

class Shader(fragmentPath: String, vertexPath: String) {
    private val uniformCache: Object2IntMap<String> = Object2IntOpenHashMap()

    private val id by mainThread {
        createShaderProgram(
            loadShader(ShaderType.VERTEX_SHADER, LambdaResource("shaders/vertex/$vertexPath.vert")),
            loadShader(ShaderType.FRAGMENT_SHADER, LambdaResource("shaders/fragment/$fragmentPath.frag"))
        )
    }

    constructor(path: String) : this(path, path)

    fun use() {
        glUseProgram(id)
        set("u_Projection", RenderMain.projectionMatrix)
        set("u_ModelView", RenderMain.modelViewMatrix)
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