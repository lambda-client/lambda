package com.lambda.graphics.buffer.vao

import com.lambda.graphics.buffer.vao.vertex.BufferUsage
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.gl.Memory.address
import com.lambda.graphics.gl.Memory.byteBuffer
import com.lambda.graphics.gl.Memory.capacity
import com.lambda.graphics.gl.Memory.color
import com.lambda.graphics.gl.Memory.copy
import com.lambda.graphics.gl.Memory.float
import com.lambda.graphics.gl.Memory.int
import com.lambda.graphics.gl.Memory.vec2
import com.lambda.graphics.gl.Memory.vec3
import com.lambda.graphics.gl.VaoUtils
import com.lambda.graphics.gl.VaoUtils.bindIndexBuffer
import com.lambda.graphics.gl.VaoUtils.bindVertexArray
import com.lambda.graphics.gl.VaoUtils.bindVertexBuffer
import com.lambda.graphics.gl.VaoUtils.bufferData
import com.lambda.graphics.gl.VaoUtils.unbindIndexBuffer
import com.lambda.graphics.gl.VaoUtils.unbindVertexArray
import com.lambda.graphics.gl.VaoUtils.unbindVertexBuffer
import com.lambda.threading.runGameScheduled
import org.lwjgl.opengl.GL30C.*
import java.awt.Color
import java.nio.ByteBuffer

class VAO(
    private val vertexMode: VertexMode,
    attribGroup: VertexAttrib.Group,
    private val bufferUsage: BufferUsage = BufferUsage.DYNAMIC
) : IRenderContext {
    private var vao = 0
    private var vbo = 0
    private var ibo = 0

    private val objectSize: Int

    private var vertices: ByteBuffer
    private var verticesPointer: Long
    private var verticesPosition: Long

    private var indices: ByteBuffer
    private var indicesPointer: Long
    private var indicesCount = 0
    private var uploadedIndices = 0

    private var vertexIndex = 0

    init {
        val stride = attribGroup.stride
        objectSize = stride * vertexMode.indicesCount

        vertices = byteBuffer(objectSize * 256 * 4)
        verticesPointer = address(vertices)
        verticesPosition = verticesPointer
        indices = byteBuffer(vertexMode.indicesCount * 512 * 4)
        indicesPointer = address(indices)

        vao = glGenVertexArrays()
        bindVertexArray(vao)

        vbo = glGenBuffers()
        bindVertexBuffer(vbo)

        ibo = glGenBuffers()
        bindIndexBuffer(ibo)

        var pointer = 0L
        attribGroup.attributes.forEachIndexed { index, attrib ->
            VaoUtils.enableVertexAttribute(index)
            VaoUtils.vertexAttribute(index, attrib.componentCount, attrib.gl, attrib.normalized, stride, pointer)
            pointer += attrib.size
        }

        unbindVertexArray()
        unbindVertexBuffer()
        unbindIndexBuffer()
    }

    override fun vec3(x: Double, y: Double, z: Double): VAO {
        verticesPosition += vec3(verticesPosition, x, y, z)
        return this
    }

    override fun vec2(x: Double, y: Double): VAO {
        verticesPosition += vec2(verticesPosition, x, y)
        return this
    }

    override fun float(v: Double): VAO {
        verticesPosition += float(verticesPosition, v)
        return this
    }

    override fun color(color: Color): VAO {
        verticesPosition += color(verticesPosition, color)
        return this
    }

    override fun end() = vertexIndex++

    override fun putLine(vertex1: Int, vertex2: Int) {
        growIndices(2)
        val p = indicesPointer + indicesCount * 4L

        int(p + 0, vertex1)
        int(p + 4, vertex2)
        indicesCount += 2
    }

    override fun putTriangle(vertex1: Int, vertex2: Int, vertex3: Int) {
        growIndices(3)
        val p = indicesPointer + indicesCount * 4L

        int(p + 0, vertex1)
        int(p + 4, vertex2)
        int(p + 8, vertex3)
        indicesCount += 3
    }

    override fun putQuad(vertex1: Int, vertex2: Int, vertex3: Int, vertex4: Int) {
        growIndices(6)
        val p = indicesPointer + indicesCount * 4L

        int(p + 0, vertex1)
        int(p + 4, vertex2)
        int(p + 8, vertex3)
        int(p + 12, vertex3)
        int(p + 16, vertex4)
        int(p + 20, vertex1)
        indicesCount += 6
    }

    override fun grow(amount: Int) {
        val cap = vertices.capacity
        if ((vertexIndex + amount + 1) * objectSize < cap) return

        val offset = verticesPosition - verticesPointer
        var newSize = cap * 2
        if (newSize % objectSize != 0) newSize += newSize % objectSize
        val newVertices = byteBuffer(newSize)

        val from = address(vertices)
        val to = address(newVertices)
        copy(from, to, offset)

        vertices = newVertices
        verticesPointer = address(vertices)
        verticesPosition = verticesPointer + offset
    }

    private fun growIndices(amount: Int) {
        val cap = indices.capacity
        if ((indicesCount + amount) * 4 < cap) return

        var newSize = cap * 2
        if (newSize % vertexMode.indicesCount != 0) newSize += newSize % (vertexMode.indicesCount * 4)
        val newIndices = byteBuffer(newSize)

        val from = address(indices)
        val to = address(newIndices)
        copy(from, to, indicesCount * 4L)

        indices = newIndices
        indicesPointer = address(indices)
    }

    override fun render() {
        if (uploadedIndices <= 0) return

        bindVertexArray(vao)
        glDrawElements(vertexMode.gl, uploadedIndices, GL_UNSIGNED_INT, 0)
        unbindVertexArray()
    }

    override fun upload() {
        if (indicesCount <= 0) return

        val vboData = vertices.limit((verticesPosition - verticesPointer).toInt())
        val iboData = indices.limit(indicesCount * 4)

        bindVertexBuffer(vbo)
        bufferData(GL_ARRAY_BUFFER, vboData, bufferUsage.gl)
        unbindVertexBuffer()

        bindIndexBuffer(ibo)
        bufferData(GL_ELEMENT_ARRAY_BUFFER, iboData, bufferUsage.gl)
        unbindIndexBuffer()

        uploadedIndices = indicesCount
    }

    override fun clear() {
        verticesPosition = verticesPointer
        vertexIndex = 0
        indicesCount = 0
        uploadedIndices = 0
    }

    protected fun finalize() {
        runGameScheduled {
            glDeleteBuffers(ibo)
            glDeleteBuffers(vbo)
            glDeleteVertexArrays(vao)
        }
    }
}
