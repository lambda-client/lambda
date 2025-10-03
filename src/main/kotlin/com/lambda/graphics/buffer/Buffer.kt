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

package com.lambda.graphics.buffer

import org.lwjgl.opengl.GL46.*
import java.nio.ByteBuffer

class Buffer private constructor(
    val target: Int,
    var buffer: Int,

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
    val access: Int,
) {
    fun bind() = glBindBuffer(target, buffer)
    fun unbind() = glBindBuffer(target, 0)

    /**
     * Execute the [block] in a bound context
     */
    fun bind(block: Buffer.() -> Unit) {
        bind()
        block(this)
        unbind()
    }

    /**
     * Allocates the buffer with the specified data.
     *
     * @param data The data to put in the new allocated buffer
     *
     * @see <a href="https://docs.gl/gl4/glBufferData">glBufferData</a>
     */
    fun allocate(data: ByteBuffer) =
        bind { glBufferData(target, data, GL_DYNAMIC_DRAW) }

    /**
     * Allocates the buffer with the specified size.
     *
     * @param data The data to put in the new allocated buffer
     *
     * @see <a href="https://docs.gl/gl4/glBufferData">glBufferData</a>
     */
    fun allocate(size: Long) =
        bind { glBufferData(target, size, GL_DYNAMIC_DRAW) }

    /**
     * Update the current buffer without re-allocating.
     *
     * @throws [IllegalArgumentException] if the target or usage is invalid
     *
     * @see <a href="https://docs.gl/gl4/glBufferSubData">glBufferSubData</a>
     */
    fun update(offset: Long, data: ByteBuffer) {
        check(offset > -1) { "Cannot have negative buffer offsets" }
        check(access and GL_DYNAMIC_STORAGE_BIT != 0) { "Buffer contents cannot be modified because the buffer was created without the GL_DYNAMIC_STORAGE_BIT set." }

        bind { glBufferSubData(target, offset, data) }
    }

    /**
     * Update the current buffer without re-allocating.
     *
     * @throws [IllegalArgumentException] if the target or usage is invalid
     *
     * @see <a href="https://docs.gl/gl4/glBufferSubData">glBufferSubData</a>
     */
    fun update(offset: Long, size: Long, data: Long) {
        check(offset > -1) { "Cannot have negative buffer offsets" }
        check(size > -1) { "Cannot have negative sized buffers" }
        check(access and GL_DYNAMIC_STORAGE_BIT != 0) { "Buffer contents cannot be modified because the buffer was created without the GL_DYNAMIC_STORAGE_BIT set." }

        bind { nglBufferSubData(target, offset, size, data) }
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
    fun storage(data: ByteBuffer) = bind { glBufferStorage(target, data, access) }

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
    fun storage(size: Long) {
        check(size > -1) { "Cannot have negative sized buffers" }
        bind { glBufferStorage(target, size, access) }
    }

    fun storage(size: Int) = storage(size.toLong())

    /**
     * Maps a specified region of the buffer's data store into client memory, processes it using the provided lambda, and
     * then unmaps the buffer.
     *
     * If [access] contains the `GL_MAP_PERSISTENT_BIT` flag, the buffer will not be unmapped.
     *
     * @param size      Specifies the length of the range to be mapped.
     * @param offset    Specifies the starting offset within the buffer of the range to be mapped.
     * @param block     Lambda scope with the mapped buffer passed in
     *
     * @see <a href="https://en.wikipedia.org/wiki/Direct_memory_access">Direct memory access</a>
     */
    fun map(offset: Long, size: Long, block: (ByteBuffer) -> Unit = {}): ByteBuffer {
        check(offset > -1) { "Cannot have negative buffer offsets" }
        check(size > -1) { "Cannot have negative sized buffers" }

        check(offset + size <= glGetBufferParameteri(target, GL_BUFFER_SIZE))
        { "Segmentation fault Size $size + Offset $offset > Buffer ${glGetBufferParameteri(target, GL_BUFFER_SIZE)}." }

        check(glGetBufferParameteri(target, GL_BUFFER_MAPPED) == GL_FALSE)
        { "Buffer is already mapped." }

        check(access and GL_MAP_WRITE_BIT != 0 || access and GL_MAP_READ_BIT != 0)
        { "Neither GL_MAP_READ_BIT nor GL_MAP_WRITE_BIT is set." }

        check((access and GL_MAP_READ_BIT != 0 &&
                (access and GL_MAP_INVALIDATE_RANGE_BIT != 0 ||
                        access and GL_MAP_INVALIDATE_BUFFER_BIT != 0 ||
                        access and GL_MAP_UNSYNCHRONIZED_BIT != 0)) ||
                access and GL_MAP_WRITE_BIT != 0
        ) { "GL_MAP_READ_BIT is set and any of GL_MAP_INVALIDATE_RANGE_BIT, GL_MAP_INVALIDATE_BUFFER_BIT or GL_MAP_UNSYNCHRONIZED_BIT is set." }

        bind()

        val sharedRegion = glMapBufferRange(target, offset, size, access)
        check(sharedRegion != null) { "Could not map the buffer" }

        block(sharedRegion)

        if (access and GL_MAP_PERSISTENT_BIT == 0)
            check(glUnmapBuffer(target)) { "An unknown error occurred due to GPU memory availability of buffer corruption." }

        unbind()

        return sharedRegion
    }

    /**
     * Indicates modifications to a range of a mapped buffer.
     */
    fun flushMappedRange(offset: Long, size: Long) = bind { glFlushMappedBufferRange(target, offset, size) }

    /**
     * Copies all or part of one buffer object's data store to the data store of another buffer object.
     */
    // fun copy(dst: Buffer, readOffset: Long, writeOffset: Long, size: Long) {}

    /**
     * Deletes the buffer object and creates a new one
     */
    fun orphan() {
        delete()
        create()
    }

    /**
     * Deletes the buffer and mark all allocated data as free
     */
    fun delete() {
        glDeleteBuffers(buffer)
        buffer = -69
    }

    /**
     * Creates a new buffer
     */
    fun create() {
        check(buffer == -69) { "Cannot create a new buffer if the previous one is not deleted" }
        buffer = glGenBuffers()
    }

    init {
        check(access and GL_MAP_COHERENT_BIT == 0 || access and GL_MAP_PERSISTENT_BIT != 0)
        { "GL_MAP_COHERENT_BIT requires GL_MAP_PERSISTENT_BIT flag." }

        check(access and GL_MAP_PERSISTENT_BIT == 0 || (access and (GL_MAP_READ_BIT or GL_MAP_WRITE_BIT) != 0))
        { "GL_MAP_PERSISTENT_BIT requires GL_MAP_READ_BIT or GL_MAP_WRITE_BIT." }
    }

    companion object {
        /**
         * Creates a buffer.
         *
         * If no [buffer] is provided, one will be created.
         *
         * @see [Buffer.access]
         */
        fun create(target: Int, access: Int, buffer: Int = glGenBuffers(), block: Buffer.() -> Unit = {}) =
            Buffer(target, buffer, access).apply { block() }
    }
}
