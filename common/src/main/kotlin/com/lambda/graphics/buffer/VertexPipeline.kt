package com.lambda.graphics.buffer

import com.lambda.Lambda.LOG
import com.lambda.graphics.buffer.vertex.ElementBuffer
import com.lambda.graphics.buffer.vertex.VertexArray
import com.lambda.graphics.buffer.vertex.VertexBuffer
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.gl.Matrices
import com.lambda.graphics.gl.Memory
import com.lambda.graphics.gl.Memory.address
import com.lambda.graphics.gl.Memory.byteBuffer
import com.lambda.graphics.gl.Memory.int
import com.lambda.graphics.gl.Memory.vector2f
import com.lambda.graphics.gl.Memory.vector3f
import com.lambda.graphics.gl.kibibyte
import org.joml.Vector4d
import org.lwjgl.opengl.GL20C.*
import java.awt.Color

class VertexPipeline(
    private val mode: VertexMode,
    attributes: VertexAttrib.Group,
) : IRenderContext {
    private val stride = attributes.stride
    private val size = stride * mode.indicesCount

    private val vao = VertexArray()
    private val vbo = VertexBuffer(mode, attributes)
    private val ebo = ElementBuffer(mode)

    private var vertices = byteBuffer(size * 1.kibibyte)
    private var verticesPointer = address(vertices)
    private var verticesPosition = verticesPointer

    private var indices = byteBuffer(mode.indicesCount * 2.kibibyte)
    private var indicesPointer = address(indices)
    private var indicesCount = 0
    private var uploadedIndices = 0

    private var vertexIndex = 0

    override fun vec3(x: Double, y: Double, z: Double): VertexPipeline {
        verticesPosition += vector3f(verticesPosition, x, y, z)
        return this
    }

    override fun vec2(x: Double, y: Double): VertexPipeline {
        verticesPosition += vector2f(verticesPosition, x, y)
        return this
    }

    override fun vec3m(x: Double, y: Double, z: Double): IRenderContext {
        Matrices.vertexTransformer?.let { mat ->
            val vec = Vector4d(x, y, z, 1.0).apply(mat::transform)
            vec3(vec.x, vec.y, vec.z)
        } ?: vec3(x, y, z)

        return this
    }

    override fun vec2m(x: Double, y: Double): IRenderContext {
        Matrices.vertexTransformer?.let { mat ->
            val vec = Vector4d(x, y, 0.0, 1.0).apply(mat::transform)
            vec2(vec.x, vec.y)
        } ?: vec2(x, y)
        return this
    }

    override fun float(v: Double): VertexPipeline {
        verticesPosition += Memory.float(verticesPosition, v)
        return this
    }

    override fun color(color: Color): VertexPipeline {
        verticesPosition += Memory.color(verticesPosition, color)
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
        val position = indicesPointer + indicesCount * 4L

        int(position + 0, vertex1)
        int(position + 4, vertex2)
        int(position + 8, vertex3)
        indicesCount += 3
    }

    override fun putQuad(vertex1: Int, vertex2: Int, vertex3: Int, vertex4: Int) {
        growIndices(6)
        val position = indicesPointer + indicesCount * 4L

        int(position + 0, vertex1)
        int(position + 4, vertex2)
        int(position + 8, vertex3)
        int(position + 12, vertex3)
        int(position + 16, vertex4)
        int(position + 20, vertex1)
        indicesCount += 6
    }

    override fun grow(amount: Int) {
        val cap = vertices.capacity()
        if ((vertexIndex + amount + 1) * size < cap) return

        val offset = verticesPosition - verticesPointer
        var newSize = cap * 2
        if (newSize % size != 0) newSize += newSize % size
        val newVertices = byteBuffer(newSize)

        val from = address(vertices)
        val to = address(newVertices)
        Memory.copy(from, to, offset)

        vbo.grow(newSize.toLong())

        vertices = newVertices
        verticesPointer = address(vertices)
        verticesPosition = verticesPointer + offset
    }

    private fun growIndices(amount: Int) {
        val cap = indices.capacity()
        if ((indicesCount + amount) * 4 < cap) return

        var newSize = cap * 2
        if (newSize % mode.indicesCount != 0) newSize += newSize % (mode.indicesCount * 4)
        val newIndices = byteBuffer(newSize)

        val from = address(indices)
        val to = address(newIndices)
        Memory.copy(from, to, indicesCount * 4L)

        ebo.grow(newSize.toLong())

        indices = newIndices
        indicesPointer = address(indices)
    }

    override fun render() {
        if (uploadedIndices <= 0) return

        vao.bind()
        glDrawElements(mode.gl, uploadedIndices, GL_UNSIGNED_INT, 0)
        vao.bind(0)
    }

    override fun upload() {
        if (indicesCount <= 0) return

        val vboData = vertices.limit((verticesPosition - verticesPointer).toInt())
        val eboData = indices.limit(indicesCount * 4)

        vbo.upload(vboData, 0)?.let(LOG::error)
        ebo.upload(eboData, 0)?.let(LOG::error)

        uploadedIndices = indicesCount
    }

    override fun clear() {
        verticesPosition = verticesPointer
        vertexIndex = 0
        indicesCount = 0
        uploadedIndices = 0
    }

    init {
        // All the buffers have been generated, all we have to
        // do now it bind them correctly and populate them
        vao.bind()
        vbo.bind()
        ebo.bind()

        // Populate the buffers
        attributes.attributes
            .foldIndexed(0L) { index, pointer, attrib ->
                glEnableVertexAttribArray(index)
                glVertexAttribPointer(index, attrib.componentCount, attrib.gl, attrib.normalized, stride, pointer)

                attrib.size.toLong()
            }

        // Unbind everything to avoid accidental modification
        vao.bind(0)
        vbo.bind(0)
        ebo.bind(0)
    }
}
