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

package com.lambda.graphics.pipeline

import com.lambda.graphics.buffer.vertex.VertexArray
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import org.lwjgl.opengl.GL32C.*

/**
 * A GPU vertex processing pipeline that manages Vertex Array Objects (VAO) and associated buffers.
 * Handles vertex data storage, attribute binding, and rendering operations.
 *
 * @property vertexMode The primitive type used for rendering (e.g., triangles, lines)
 * @property attributes Group of vertex attributes defining the data layout
 *
 * @see VertexMode for vertex configuration
 * @see VertexAttrib.Group for attribute configuration
 * @see PersistentBuffer for buffer management
 */
class VertexPipeline(
    private val vertexMode: VertexMode,
    private val attributes: VertexAttrib.Group
) {
    private val vao = VertexArray(vertexMode, attributes)
    private val vbo = PersistentBuffer(GL_ARRAY_BUFFER, attributes.stride)
    private val ibo = PersistentBuffer(GL_ELEMENT_ARRAY_BUFFER, UInt.SIZE_BYTES)

    /**
     * Direct access to the vertex buffer's underlying byte storage
     */
    val vertices get() = vbo.byteBuffer

    /**
     * Direct access to the index buffer's underlying byte storage
     */
    val indices get() = ibo.byteBuffer

    /**
     * Submits a draw call to the GPU using currently uploaded data
     * Binds VAO and issues glDrawElementsBaseVertex command
     */
    fun render() = vao.render(
        indicesSize = indices.bytesPut - ibo.uploadOffset,
        indicesPointer = indices.pointer + ibo.uploadOffset,
        verticesOffset = vbo.uploadOffset
    )

    /**
     * Builds and renders data constructed by [VertexBuilder]
     *
     * It is recommended to use this method for direct data transfer
     * to avoid the overhead caused by [VertexBuilder]
     *
     * Uploads buffered data to GPU memory
     *
     * Note: only one vertex builder could be built and uploaded within 1 batch
     */
    fun immediate(block: VertexBuilder.() -> Unit) {
        VertexBuilder(this).apply(block)
        uploadInternal(); render(); clear()
    }

    /**
     * Builds data constructed by [VertexBuilder]
     *
     * It is recommended to use this method for direct data transfer
     * to avoid the overhead caused by [VertexBuilder]
     *
     * Uploads buffered data to GPU memory
     *
     * Note: only one vertex builder could be built and uploaded within 1 batch
     */
    fun upload(block: VertexBuilder.() -> Unit) {
        VertexBuilder(this).apply(block)
        uploadInternal()
    }

    /**
     * Builds data constructed by [VertexBuilder]
     *
     * Uploads buffered data to GPU memory
     *
     * Note: only one vertex builder could be built and uploaded within 1 batch
     */
    fun upload(builder: VertexBuilder) {
        builder.uploadTo(this)
        uploadInternal()
    }

    /**
     * Creates a [VertexBuilder]
     *
     * Note: only one vertex builder could be built and uploaded within 1 batch
     */
    fun build(block: VertexBuilder.() -> Unit = {}) =
        VertexBuilder().apply(block)

    /**
     * Uploads buffered data to GPU memory
     */
    private fun uploadInternal() {
        vbo.upload()
        ibo.upload()
    }

    /**
     * Finalizes the current draw batch and prepares for new data
     */
    fun end() {
        vbo.end()
        ibo.end()
    }

    /**
     * Synchronizes buffer states between frames
     * Should be called at the end of each frame
     */
    fun sync() {
        vbo.sync()
        ibo.sync()
    }

    /**
     * Resets both vertex and index buffers
     */
    fun clear() {
        vbo.clear()
        ibo.clear()
    }

    init {
        vao.bind { vbo.use(attributes::link) }
    }
}
