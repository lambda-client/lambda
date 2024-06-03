package com.lambda.graphics.gl

import net.minecraft.client.render.BufferRenderer
import org.lwjgl.opengl.GL30C.*
import java.nio.ByteBuffer

object VaoUtils {
    @JvmField
    var lastIbo = 0
    private var prevIbo = 0

    fun bindVertexArray(vao: Int) {
        glBindVertexArray(vao)
        BufferRenderer.currentVertexBuffer = null
    }

    fun bindVertexBuffer(vbo: Int) =
        glBindBuffer(GL_ARRAY_BUFFER, vbo)

    fun bindIndexBuffer(ibo: Int) {
        if (ibo != 0) prevIbo = lastIbo
        val targetIbo = if (ibo != 0) ibo else prevIbo
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, targetIbo)
    }

    fun unbindVertexArray() =
        bindVertexArray(0)

    fun unbindVertexBuffer() =
        bindVertexBuffer(0)

    fun unbindIndexBuffer() =
        bindIndexBuffer(0)

    fun enableVertexAttribute(i: Int) =
        glEnableVertexAttribArray(i)

    fun vertexAttribute(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, pointer: Long) =
        glVertexAttribPointer(index, size, type, normalized, stride, pointer)

    fun bufferData(target: Int, data: ByteBuffer, usage: Int) =
        glBufferData(target, data, usage)
}
