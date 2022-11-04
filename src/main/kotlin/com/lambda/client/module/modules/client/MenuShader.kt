package com.lambda.client.module.modules.client

import com.lambda.client.LambdaMod
import com.lambda.client.commons.extension.next
import com.lambda.client.commons.extension.previous
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.Wrapper
import net.minecraft.client.renderer.OpenGlHelper.glGetShaderi
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11.GL_QUADS
import org.lwjgl.opengl.GL20.*
import java.util.*

object MenuShader : Module(
    name = "MenuShader",
    description = "Shows a shader on the main menu",
    category = Category.CLIENT,
    showOnArray = false,
    enabledByDefault = true
) {
    private val mode by setting("Mode", Mode.SET)
    private val shader = setting("Shader", ShaderEnum.CLOUDS, { mode == Mode.SET })

    private enum class Mode {
        RANDOM, SET
    }

    @Suppress("UNUSED")
    private enum class ShaderEnum(val path: String) {
        BLUE_GRID("/assets/shaders/menu/bluegrid.fsh"),
        BLUE_NEBULA("/assets/shaders/menu/bluenebula.fsh"),
        BLUE_VORTEX("/assets/shaders/menu/bluevortex.fsh"),
        CAVE("/assets/shaders/menu/cave.fsh"),
        CLOUDS("/assets/shaders/menu/clouds.fsh"),
        DOUGHNUTS("/assets/shaders/menu/doughnuts.fsh"),
        FIRE("/assets/shaders/menu/fire.fsh"),
        JUPITER("/assets/shaders/menu/jupiter.fsh"),
        MATRIX("/assets/shaders/menu/matrix.fsh"),
        MINECRAFT("/assets/shaders/menu/minecraft.fsh"),
        PURPLE_GRID("/assets/shaders/menu/purplegrid.fsh"),
        PURPLE_MIST("/assets/shaders/menu/purplemist.fsh"),
        RED_GLOW("/assets/shaders/menu/redglow.fsh"),
        SKY("/assets/shaders/menu/sky.fsh"),
        SNAKE("/assets/shaders/menu/snake.fsh"),
        SPACE("/assets/shaders/menu/space.fsh"),
        SPACE2("/assets/shaders/menu/space2.fsh"),
        STORM("/assets/shaders/menu/storm.fsh"),
        TRIANGLE("/assets/shaders/menu/triangle.fsh")
    }

    private const val VERTEX_SHADER = "/assets/shaders/menu/vert.vsh"

    private val shaderCache = EnumMap<ShaderEnum, ShaderProgram>(ShaderEnum::class.java)
    private var initTime: Long = 0x22
    private var currentShader = getShader()

    @JvmStatic
    fun render() {
        currentShader.render()
    }

    @JvmStatic
    fun reset() {
        this.initTime = System.currentTimeMillis()
        currentShader = getShader()
    }

    @JvmStatic
    fun setNextShader() {
        shader.value = shader.value.next()
    }

    @JvmStatic
    fun setPreviousShader() {
        shader.value = shader.value.previous()
    }

    private fun getShader(): ShaderProgram {
        val shader = if (mode == Mode.RANDOM) {
            ShaderEnum.values().random()
        } else {
            shader.value
        }

        return shaderCache.getOrPut(shader) {
            ShaderProgram(shader)
        }
    }

    private class ShaderProgram(shader: ShaderEnum) {
        private val id: Int
        private val timeUniform: Int
        private val mouseUniform: Int
        private val resolutionUniform: Int

        init {
            val vertexShaderID = createShader(VERTEX_SHADER, GL_VERTEX_SHADER)
            val fragShaderID = createShader(shader.path, GL_FRAGMENT_SHADER)
            val id = glCreateProgram()

            glAttachShader(id, vertexShaderID)
            glAttachShader(id, fragShaderID)

            glLinkProgram(id)
            val linked = glGetProgrami(id, GL_LINK_STATUS)
            if (linked == 0) {
                glDeleteProgram(id)
                LambdaMod.LOG.error(glGetProgramInfoLog(id, 1024))
                throw IllegalStateException("Shader failed to link")
            }

            glDetachShader(id, vertexShaderID)
            glDetachShader(id, fragShaderID)
            glDeleteShader(vertexShaderID)
            glDeleteShader(fragShaderID)

            this.id = id
            glUseProgram(id)
            timeUniform = glGetUniformLocation(id, "time")
            mouseUniform = glGetUniformLocation(id, "mouse")
            resolutionUniform = glGetUniformLocation(id, "resolution")
            glUseProgram(0)
        }

        private fun createShader(path: String, shaderType: Int): Int {
            val srcString = javaClass.getResourceAsStream(path)!!.readBytes().decodeToString()
            val id = glCreateShader(shaderType)

            glShaderSource(id, srcString)
            glCompileShader(id)

            val compiled = glGetShaderi(id, GL_COMPILE_STATUS)
            if (compiled == 0) {
                glDeleteShader(id)
                LambdaMod.LOG.error(glGetShaderInfoLog(id, 1024))
                throw IllegalStateException("Failed to compile shader: $path")
            }

            return id
        }

        fun render() {
            val width = Wrapper.minecraft.displayWidth.toFloat()
            val height = Wrapper.minecraft.displayHeight.toFloat()
            val mouseX = Mouse.getX() - 1.0f
            val mouseY = height - Mouse.getY() - 1.0f

            glUseProgram(id)
            glUniform2f(resolutionUniform, width, height)
            glUniform2f(mouseUniform, mouseX / width, (height - 1.0f - mouseY) / height)
            glUniform1f(timeUniform, ((System.currentTimeMillis() - initTime) / 1000.0).toFloat())

            val tessellator = Tessellator.getInstance()
            val buffer = tessellator.buffer

            buffer.begin(GL_QUADS, DefaultVertexFormats.POSITION)
            buffer.pos(-1.0, -1.0, 0.0).endVertex()
            buffer.pos(1.0, -1.0, 0.0).endVertex()
            buffer.pos(1.0, 1.0, 0.0).endVertex()
            buffer.pos(-1.0, 1.0, 0.0).endVertex()
            tessellator.draw()

            glUseProgram(0)
        }
    }
}