package com.lambda.client.util.shader

import org.lwjgl.opengl.GL20
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

class ShaderSandbox(fragmentShaderLocation: String) {

    private val programId: Int
    private val timeUniform: Int
    private val mouseUniform: Int
    private val resolutionUniform: Int

    fun useShader(width: Int, height: Int, mouseX: Float, mouseY: Float, time: Float) {
        GL20.glUseProgram(programId)
        GL20.glUniform2f(resolutionUniform, width.toFloat(), height.toFloat())
        GL20.glUniform2f(mouseUniform, mouseX / width, 1.0f - mouseY / height)
        GL20.glUniform1f(timeUniform, time)
    }

    @Throws(IOException::class) private fun createShader(check: String, inputStream: InputStream, shaderType: Int): Int {
        val shader = GL20.glCreateShader(shaderType)
        GL20.glShaderSource(shader, readStreamToString(inputStream))
        GL20.glCompileShader(shader)
        val compiled = GL20.glGetShaderi(shader, 35713)
        if (compiled == 0) {
            System.err.println(GL20.glGetShaderInfoLog(shader, GL20.glGetShaderi(shader, 35716)))
            System.err.println("Caused by $check")
            throw IllegalStateException("Failed to compile shader: $check")
        }
        return shader
    }

    @Throws(IOException::class) private fun readStreamToString(inputStream: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(512)
        var read: Int
        while (inputStream.read(buffer, 0, buffer.size).also { read = it } != -1) out.write(buffer, 0, read)
        return out.toString(StandardCharsets.UTF_8.toString())
    }

    init {
        val program = GL20.glCreateProgram()
        GL20.glAttachShader(program, createShader("/assets/minecraft/shaders/menu/passthrough.vsh", ShaderSandbox::class.java.getResourceAsStream("/assets/minecraft/shaders/menu/passthrough.vsh"), 35633))
        GL20.glAttachShader(program, createShader(fragmentShaderLocation, ShaderSandbox::class.java.getResourceAsStream(fragmentShaderLocation), 35632))
        GL20.glLinkProgram(program)
        val linked = GL20.glGetProgrami(program, 35714)
        if (linked == 0) {
            System.err.println(GL20.glGetProgramInfoLog(program, GL20.glGetProgrami(program, 35716)))
            throw IllegalStateException("Shader failed to link")
        }
        programId = program
        GL20.glUseProgram(program)
        timeUniform = GL20.glGetUniformLocation(program, "time")
        mouseUniform = GL20.glGetUniformLocation(program, "mouse")
        resolutionUniform = GL20.glGetUniformLocation(program, "resolution")
        GL20.glUseProgram(0)
    }
}
