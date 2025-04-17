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

package com.lambda.graphics.buffer

import org.lwjgl.opengl.GL46.*
import java.nio.ByteBuffer

abstract class Buffer(
    /**
     * Specifies how many buffer must be used
     *
     * | Number of Buffers | Purpose                                                                                                       |
     * |-------------------|---------------------------------------------------------------------------------------------------------------|
     * | 1 Buffer          | Simple operations like storing vertex data, reading from the framebuffer, etc.                                |
     * | 2 Buffers         | Increase throughput by not having to explicitly sync memory.                                                  |
     * | 3 Buffers         | If the driver run alongside the CPU and GPU, then each must have their own buffer to avoid stalling.          |
     */
    val buffers: Int = 1,

    /**
     * Edge case to handle vertex arrays
     */
    val isVertexArray: Boolean = false,
) {
    /**
     * Specifies how the buffers are used
     *
     * | Buffer Usage                     | Description                                                     |
     * |----------------------------------|-----------------------------------------------------------------|
     * | [GL_STREAM_DRAW]                 | Data is set once and used a few times for drawing.              |
     * | [GL_STREAM_READ]                 | Data is set once and used a few times for reading.              |
     * | [GL_STREAM_COPY]                 | Data is set once and used a few times for copying.              |
     * | [GL_STATIC_DRAW]                 | Data is set once and used many times for drawing.               |
     * | [GL_STATIC_READ]                 | Data is set once and used many times for reading.               |
     * | [GL_STATIC_COPY]                 | Data is set once and used many times for copying.               |
     * | [GL_DYNAMIC_DRAW]                | Data is modified repeatedly and used many times for drawing.    |
     * | [GL_DYNAMIC_READ]                | Data is modified repeatedly and used many times for reading.    |
     * | [GL_DYNAMIC_COPY]                | Data is modified repeatedly and used many times for copying.    |
     *
     * @see <a href="https://www.khronos.org/opengl/wiki/Buffer_Object">Buffer object</a>
     */
    abstract val usage: Int

    /**
     * Specifies the target to which the buffer object is bound which must be one
     * of the following:
     *
     * | Buffer Binding Target           | Purpose                              |
     * |---------------------------------|--------------------------------------|
     * | [GL_ARRAY_BUFFER]               | Vertex attributes                    |
     * | [GL_ATOMIC_COUNTER_BUFFER]      | Atomic counter storage               |
     * | [GL_COPY_READ_BUFFER]           | Buffer copy source                   |
     * | [GL_COPY_WRITE_BUFFER]          | Buffer copy destination              |
     * | [GL_DISPATCH_INDIRECT_BUFFER]   | Indirect compute dispatch commands   |
     * | [GL_DRAW_INDIRECT_BUFFER]       | Indirect command arguments           |
     * | [GL_ELEMENT_ARRAY_BUFFER]       | Vertex array indices                 |
     * | [GL_PIXEL_PACK_BUFFER]          | Pixel read target                    |
     * | [GL_PIXEL_UNPACK_BUFFER]        | Texture data source                  |
     * | [GL_QUERY_BUFFER]               | Query result buffer                  |
     * | [GL_SHADER_STORAGE_BUFFER]      | Read-write storage for shaders       |
     * | [GL_TEXTURE_BUFFER]             | Texture data buffer                  |
     * | [GL_TRANSFORM_FEEDBACK_BUFFER]  | Transform feedback buffer            |
     * | [GL_UNIFORM_BUFFER]             | Uniform block storage                |
     *
     * @see <a href="https://www.khronos.org/opengl/wiki/Buffer_Object">Buffer object</a>
     */
    abstract val target: Int

    /**
     * Specifies a combination of access flags indicating the desired
     * access to the mapping range and must contain one or more of the following:
     *
     * | Flag                           | Description                                         | Information                                                                               |
     * |--------------------------------|-----------------------------------------------------|-------------------------------------------------------------------------------------------|
     * | [GL_MAP_READ_BIT]              | Allows reading buffer data.                         | Buffer must be created with this flag. Undefined if not included in access.               |
     * | [GL_MAP_WRITE_BIT]             | Allows modifying buffer data.                       | Buffer must be created with this flag. Undefined if not included in access.               |
     * | [GL_MAP_PERSISTENT_BIT]        | Enables persistent mapping during GL operations.    | Requires buffer to be created with [GL_MAP_PERSISTENT_BIT].                               |
     * | [GL_MAP_COHERENT_BIT]          | Ensures changes are visible to the GPU.             | Requires buffer creation with [GL_MAP_PERSISTENT_BIT] or explicit sync.                   |
     * | [GL_MAP_INVALIDATE_RANGE_BIT]  | Discards previous contents of the mapped range.     | Cannot be used with [GL_MAP_READ_BIT].                                                    |
     * | [GL_MAP_INVALIDATE_BUFFER_BIT] | Discards previous contents of the entire buffer.    | Cannot be used with [GL_MAP_READ_BIT].                                                    |
     * | [GL_MAP_FLUSH_EXPLICIT_BIT]    | Requires explicit flushing of modified sub-ranges.  | Only valid with [GL_MAP_WRITE_BIT]. Data may be undefined if flushing is skipped.         |
     * | [GL_MAP_UNSYNCHRONIZED_BIT]    | Skips synchronization before mapping.               | May cause data corruption if buffer is accessed concurrently.                             |
     * | [GL_DYNAMIC_STORAGE_BIT]       | Allows updates via [glBufferSubData].               | If omitted, [glBufferSubData] will fail.                                                  |
     * | [GL_CLIENT_STORAGE_BIT]        | Hints that the buffer should prefer client storage. | Implementation-dependent optimization.                                                    |
     *
     * @see <a href="https://www.khronos.org/opengl/wiki/Buffer_Object">Buffer object</a>
     */
    abstract val access: Int

    /**
     * Index of the current buffer.
     */
    var index: Int = 0; private set

    /**
     * List of all the buffers.
     */
    private val bufferIds = IntArray(buffers)

    /**
     * Binds the buffer id to the [target].
     */
    open fun bind(id: Int) = glBindBuffer(target, id)

    /**
     * Binds current the buffer [index] to the [target].
     */
    fun bind() = bind(bufferAt(index))

    /**
     * Returns the id of the buffer based on the index.
     */
    fun bufferAt(index: Int) = bufferIds[index]

    /**
     * Swaps the buffer [index] if [buffers] is greater than 1.
     */
    fun swap() { index = (index + 1) % buffers }

    /**
     * Update the current buffer without re-allocating.
     *
     * @throws [IllegalArgumentException] if the target or usage is invalid
     *
     * @see <a href="https://registry.khronos.org/OpenGL-Refpages/gl4/html/glBufferSubData.xhtml">glBufferSubData</a>
     */
    open fun update(data: ByteBuffer, offset: Long) {
        validate()

        bind()
        glBufferSubData(target, offset, data)
        bind(0)
    }

    /**
     * Update the current buffer without re-allocating.
     *
     * @throws [IllegalArgumentException] if the target or usage is invalid
     *
     * @see <a href="https://registry.khronos.org/OpenGL-Refpages/gl4/html/glBufferSubData.xhtml">glBufferSubData</a>
     */
    open fun update(offset: Long, size: Long, data: Long) {
        validate()

        bind()
        nglBufferSubData(target, offset, size, data)
        bind(0)
    }

    /**
     * Allocates each backing buffer with the specified data.
     *
     * @param data The data to put in the new allocated buffer
     * @throws [IllegalArgumentException] if the target or usage is invalid
     *
     * @see <a href="https://registry.khronos.org/OpenGL-Refpages/gl4/html/glBufferData.xhtml">glBufferData</a>
     */
    open fun allocate(data: ByteBuffer) {
        validate()

        repeat(buffers) {
            bind()
            glBufferData(target, data, usage)
            swap()
        }

        bind(0)
    }

    /**
     * Allocates memory for each backing buffer of specified size.
     *
     * @param size The size of the new buffer
     * @throws [IllegalArgumentException] if the target or usage is invalid
     *
     * @see <a href="https://registry.khronos.org/OpenGL-Refpages/gl4/html/glBufferData.xhtml">glBufferData</a>
     */
    open fun allocate(size: Long) {
        validate()

        repeat(buffers) {
            bind()
            glBufferData(target, size.coerceAtLeast(0), usage)
            swap()
        }

        bind(0)
    }

    /**
     * Allocates new storage for the OpenGL buffer using the provided data.
     *
     * This function cannot be called twice for the same buffer.
     *
     * You cannot update the content of the buffer directly unless you are pinning memory
     * or have GL_DYNAMIC_STORAGE_BIT in the access flags.
     *
     * @throws [IllegalArgumentException] if the target or usage is invalid
     *
     * @see <a href="https://registry.khronos.org/OpenGL-Refpages/gl4/html/glBufferStorage.xhtml">glBufferStorage</a>
     */
    open fun storage(data: ByteBuffer) {
        validate()

        repeat(buffers) {
            bind()
            glBufferStorage(target, data, access)
            swap()
        }

        bind(0)
    }

    /**
     * Allocates storage for the buffer object.
     *
     * This function cannot be called twice for the same buffer.
     *
     * You cannot update the content of the buffer directly unless you are pinning memory
     * or have GL_DYNAMIC_STORAGE_BIT in the access flags.
     *
     * @param size The size of the storage buffer
     * @throws [IllegalArgumentException] if the target or usage is invalid
     *
     * @see <a href="https://registry.khronos.org/OpenGL-Refpages/gl4/html/glBufferStorage.xhtml">glBufferStorage</a>
     */
    open fun storage(size: Long) {
        validate()

        repeat(buffers) {
            bind()
            glBufferStorage(target, size.coerceAtLeast(0), access)
            swap()
        }

        bind(0)
    }

    // TODO:
    //  GL_MAP_COHERENT_BIT makes it so changes in the mapped memory are automatically visible to the gpu, no memory barrier and syncing required, but a bit slower
    //  You still need to swap after each update or you will risk having conflicting reads and writes
    //  glFlushMappedBufferRange

    /**
     * Maps a specified region of the buffer's data store into client memory, processes it using the provided lambda, and then unmaps the buffer.
     *
     * If [access] contains the `GL_MAP_PERSISTENT_BIT` flag, the buffer will not be unmapped.
     *
     * @param size      Specifies the length of the range to be mapped.
     * @param offset    Specifies the starting offset within the buffer of the range to be mapped.
     * @param block     Lambda scope with the mapped buffer passed in
     *
     * @see <a href="https://en.wikipedia.org/wiki/Direct_memory_access">Direct memory access</a>
     */
    open fun map(size: Long, offset: Long, block: (ByteBuffer) -> Unit = {}): ByteBuffer {
        validate()
        bind()

        check(offset >= 0 || size >= 0)
        { "Invalid offset or size parameter offset: $offset size: $size." }

        check(offset + size <= glGetBufferParameteri(target, GL_BUFFER_SIZE))
        { "Out of bound (is the buffer initialized?) $size + $offset > ${glGetBufferParameteri(target, GL_BUFFER_SIZE)}." }

        check(glGetBufferParameteri(target, GL_BUFFER_MAPPED) == GL_FALSE)
        { "Buffer is already mapped." }

        check(access and GL_MAP_WRITE_BIT != 0 || access and GL_MAP_READ_BIT != 0)
        { "Neither GL_MAP_READ_BIT nor GL_MAP_WRITE_BIT is set." }

        check((access and GL_MAP_READ_BIT != 0 &&
                (access and GL_MAP_INVALIDATE_RANGE_BIT != 0 ||
                        access and GL_MAP_INVALIDATE_BUFFER_BIT != 0 ||
                        access and GL_MAP_UNSYNCHRONIZED_BIT != 0)) ||
                access and GL_MAP_WRITE_BIT != 0
        )
        { "GL_MAP_READ_BIT is set and any of GL_MAP_INVALIDATE_RANGE_BIT, GL_MAP_INVALIDATE_BUFFER_BIT or GL_MAP_UNSYNCHRONIZED_BIT is set." }

        val sharedRegion = glMapBufferRange(target, offset, size, access)
            ?: throw IllegalStateException("Failed to map buffer.")

        block(sharedRegion)

        if (access and GL_MAP_PERSISTENT_BIT == 0) {
            if (!glUnmapBuffer(target))
                throw IllegalStateException("An unknown error occurred due to GPU memory availability of buffer corruption.")
        }

        bind(0)

        return sharedRegion
    }

    /**
     * Uploads the specified data to the buffer starting at the given offset.
     *
     * This abstract function should be implemented to perform the actual data transfer into the buffer.
     *
     * @param data      Data to set in memory
     * @param offset    The starting offset within the buffer of the range to be mapped
     */
    abstract fun upload(data: ByteBuffer, offset: Long)

    private fun validate() {
        check(usage in GL_STREAM_DRAW..GL_DYNAMIC_COPY)
        { "Usage is invalid, refer to the documentation table." }

        check(target in bindingCheckMappings)
        { "Target is invalid, refer to the documentation table." }

        check(access and GL_MAP_COHERENT_BIT == 0 || access and GL_MAP_PERSISTENT_BIT != 0)
        { "GL_MAP_COHERENT_BIT requires GL_MAP_PERSISTENT_BIT flag." }

        check(access and GL_MAP_PERSISTENT_BIT == 0 || (access and (GL_MAP_READ_BIT or GL_MAP_WRITE_BIT) != 0))
        { "GL_MAP_PERSISTENT_BIT requires GL_MAP_READ_BIT or GL_MAP_WRITE_BIT." }
    }

    init {
        check(buffers > 0) { "Cannot generate less than one buffer" }

        if (isVertexArray) glGenVertexArrays(bufferIds) // If there are more than 1 buffer you should expect undefined behavior, this is not the way to do it
        else glGenBuffers(bufferIds)
    }

    companion object {
        val bindingCheckMappings = mapOf(
            GL_ARRAY_BUFFER to GL_ARRAY_BUFFER_BINDING,
            GL_ATOMIC_COUNTER_BUFFER to GL_ATOMIC_COUNTER_BUFFER_BINDING,
            GL_COPY_READ_BUFFER_BINDING to GL_COPY_READ_BUFFER_BINDING,
            GL_COPY_WRITE_BUFFER_BINDING to GL_COPY_WRITE_BUFFER_BINDING,
            GL_DISPATCH_INDIRECT_BUFFER to GL_DISPATCH_INDIRECT_BUFFER_BINDING,
            GL_DRAW_INDIRECT_BUFFER to GL_DRAW_INDIRECT_BUFFER_BINDING,
            GL_ELEMENT_ARRAY_BUFFER to GL_ELEMENT_ARRAY_BUFFER_BINDING,
            GL_PIXEL_PACK_BUFFER to GL_PIXEL_PACK_BUFFER_BINDING,
            GL_PIXEL_UNPACK_BUFFER to GL_PIXEL_UNPACK_BUFFER_BINDING,
            GL_QUERY_BUFFER to GL_QUERY_BUFFER_BINDING,
            GL_SHADER_STORAGE_BUFFER to GL_SHADER_STORAGE_BUFFER_BINDING,
            GL_TEXTURE_BUFFER to GL_TEXTURE_BUFFER_BINDING,
            GL_TRANSFORM_FEEDBACK_BUFFER to GL_TRANSFORM_FEEDBACK_BUFFER_BINDING,
            GL_UNIFORM_BUFFER to GL_UNIFORM_BUFFER_BINDING,
        )

        @JvmField
        var lastIbo = 0
        var prevIbo = 0

        fun createPipelineBuffer(bufferTarget: Int) = object : Buffer(buffers = 1) {
            override val target: Int = bufferTarget

            override val usage: Int = GL_STATIC_DRAW
            override val access: Int = GL_MAP_WRITE_BIT

            override fun bind(id: Int) {
                if (bufferTarget != GL_ELEMENT_ARRAY_BUFFER) {
                    super.bind(id)
                    return
                }

                if (id != 0) prevIbo = lastIbo
                super.bind(if (id != 0) id else prevIbo)
            }

            override fun upload(data: ByteBuffer, offset: Long) = throw UnsupportedOperationException()
        }
    }
}
