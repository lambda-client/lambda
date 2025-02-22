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

import com.lambda.graphics.gl.bufferBound
import com.lambda.graphics.gl.bufferUsageValid
import com.lambda.graphics.gl.bufferValid
import org.lwjgl.opengl.GL44.*
import java.nio.ByteBuffer

abstract class Buffer(
    /**
     * Specifies how many buffer must be used
     *
     * | Number of Buffers | Purpose                                                                                                       |
     * |-------------------|---------------------------------------------------------------------------------------------------------------|
     * | 1 Buffer          | Simple operations like storing vertex data or a single texture.                                               |
     * | 2 Buffers         | Double buffering for smooth rendering (alternating between writing to one buffer while reading from another). |
     * | 3 Buffers         | Triple buffering for improved frame rate and reduced screen tearing at the cost of memory usage.              |
     *
     * In double buffering, you have a front buffer (A) and a back buffer (B). While one is displayed (A), the other (B) is being drawn to. After drawing, the buffers are swapped. However, during the swap (often synchronized with vertical retrace to avoid tearing), drawing cannot resume until it completes, potentially causing delays.
     *
     * In triple buffering, there is a front buffer (A) and two back buffers (B and C). This allows drawing to continue on the second back buffer (C) while waiting for the swap to finish between the front (A) and the first back buffer (B). This reduces idle time and improves frame rates, especially if the app runs slower than the monitor's refresh rate.
     *
     * Triple buffering helps maintain smoother frame rates, but if your app runs faster than the monitor's refresh rate, it offers little benefit as you eventually still wait for vblank synchronization.
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
     * | Buffer Usage                   | Description                                                     |
     * |--------------------------------|-----------------------------------------------------------------|
     * | GL_STREAM_DRAW                 | Data is set once and used a few times for drawing.              |
     * | GL_STREAM_READ                 | Data is set once and used a few times for reading.              |
     * | GL_STREAM_COPY                 | Data is set once and used a few times for copying.              |
     * | GL_STATIC_DRAW                 | Data is set once and used many times for drawing.               |
     * | GL_STATIC_READ                 | Data is set once and used many times for reading.               |
     * | GL_STATIC_COPY                 | Data is set once and used many times for copying.               |
     * | GL_DYNAMIC_DRAW                | Data is modified repeatedly and used many times for drawing.    |
     * | GL_DYNAMIC_READ                | Data is modified repeatedly and used many times for reading.    |
     * | GL_DYNAMIC_COPY                | Data is modified repeatedly and used many times for copying.    |
     */
    abstract val usage: Int

    /**
     * Specifies the target to which the buffer object is bound which must be one
     * of the following:
     *
     * | Buffer Binding Target         | Purpose                              |
     * |-------------------------------|--------------------------------------|
     * | GL_ARRAY_BUFFER               | Vertex attributes                    |
     * | GL_ATOMIC_COUNTER_BUFFER      | Atomic counter storage               |
     * | GL_COPY_READ_BUFFER           | Buffer copy source                   |
     * | GL_COPY_WRITE_BUFFER          | Buffer copy destination              |
     * | GL_DISPATCH_INDIRECT_BUFFER   | Indirect compute dispatch commands   |
     * | GL_DRAW_INDIRECT_BUFFER       | Indirect command arguments           |
     * | GL_ELEMENT_ARRAY_BUFFER       | Vertex array indices                 |
     * | GL_PIXEL_PACK_BUFFER          | Pixel read target                    |
     * | GL_PIXEL_UNPACK_BUFFER        | Texture data source                  |
     * | GL_QUERY_BUFFER               | Query result buffer                  |
     * | GL_SHADER_STORAGE_BUFFER      | Read-write storage for shaders       |
     * | GL_TEXTURE_BUFFER             | Texture data buffer                  |
     * | GL_TRANSFORM_FEEDBACK_BUFFER  | Transform feedback buffer            |
     * | GL_UNIFORM_BUFFER             | Uniform block storage                |
     */
    abstract val target: Int

    /**
     * Specifies a combination of access flags indicating the desired
     * access to the mapping range and must contain one or more of the following:
     *
     * | Flag                          | Description                                         | Disclaimer                                                    |
     * |-------------------------------|-----------------------------------------------------|---------------------------------------------------------------|
     * | GL_MAP_READ_BIT               | Allows reading buffer data.                         | Undefined if used without this flag.                          |
     * | GL_MAP_WRITE_BIT              | Allows modifying buffer data.                       | Undefined if used without this flag.                          |
     * | GL_MAP_PERSISTENT_BIT         | Enables persistent mapping during GL operations.    | Requires proper allocation with GL_MAP_PERSISTENT_BIT.        |
     * | GL_MAP_COHERENT_BIT           | Ensures changes are visible without extra steps.    | Without this, explicit sync is needed.                        |
     * | GL_MAP_INVALIDATE_RANGE_BIT   | Discards previous contents of the mapped range.     | Cannot be used with GL_MAP_READ_BIT.                          |
     * | GL_MAP_INVALIDATE_BUFFER_BIT  | Discards previous contents of the entire buffer.    | Cannot be used with GL_MAP_READ_BIT.                          |
     * | GL_MAP_FLUSH_EXPLICIT_BIT     | Requires explicit flushing of modified sub-ranges.  | Only with GL_MAP_WRITE_BIT. Data may be undefined if skipped. |
     * | GL_MAP_UNSYNCHRONIZED_BIT     | Skips synchronization before mapping.               | May cause data corruption if regions overlap.                 |
     */
    abstract val access: Int

    /**
     * Index of the current buffer
     */
    var index: Int = 0; private set

    /**
     * List of all the buffers
     */
    private val bufferIds = IntArray(buffers)

    /**
 * Binds the specified buffer id to this buffer's target.
 *
 * This operation makes the buffer identified by [id] the active buffer for subsequent OpenGL operations
 * on the target defined by this buffer.
 *
 * @param id the unique identifier of the buffer to bind.
 */
    open fun bind(id: Int) = glBindBuffer(target, id)

    /**
 * Binds the active buffer to its target.
 *
 * Retrieves the buffer ID associated with the current [index] using [bufferAt] and binds it
 * by calling the overloaded [bind] method.
 */
    fun bind() = bind(bufferAt(index))

    /**
 * Retrieves the buffer identifier at the specified index.
 *
 * @param index The 0-based index of the buffer ID.
 * @return The buffer identifier corresponding to the given index.
 */
    fun bufferAt(index: Int) = bufferIds[index]

    /**
     * Swaps the buffer [index] if [buffers] is greater than 1
     */
    fun swap() {
        index = (index + 1) % buffers
    }

    /**
     * Updates the current buffer's data at the given offset without reallocating its memory.
     *
     * This method uploads the provided data to the buffer using a sub-data update mechanism.
     * It verifies that the buffer's target is valid and bound before performing the update;
     * if either check fails, an IllegalArgumentException is returned.
     *
     * @param data the new data to upload into the buffer.
     * @param offset the byte offset within the buffer at which the update should start.
     * @return an IllegalArgumentException if the buffer is not valid or bound; otherwise, null.
     *
     * @see map
     */
    open fun update(
        data: ByteBuffer,
        offset: Long,
    ): Throwable? {
        if (!bufferValid(target, access))
            return IllegalArgumentException("Target is not valid. Refer to the table in the documentation")

        if (!bufferBound(target))
            return IllegalArgumentException("Target is zero bound for glBufferSubData")

        glBufferSubData(target, offset, data)

        return null
    }

    /**
     * Allocates and initializes buffer storage using the provided data.
     *
     * This function validates the buffer's target and usage before allocation. If the validation fails, it returns an
     * IllegalArgumentException. Otherwise, it binds and allocates storage for each buffer in sequence, updating the current
     * buffer index by swapping after each allocation, and finally resets the binding.
     *
     * @param data The ByteBuffer containing the initial data for the buffer.
     * @return An IllegalArgumentException if validation fails; null if allocation succeeds.
     */
    open fun allocate(data: ByteBuffer): Throwable? {
        if (!bufferValid(target, access))
            return IllegalArgumentException("Target is not valid. Refer to the table in the documentation")

        if (!bufferUsageValid(usage))
            return IllegalArgumentException("Buffer usage is invalid")

        repeat(buffers) {
            bind()
            glBufferData(target, data, usage)
            swap()
        }

        bind(0)

        return null
    }

    /**
     * Allocates memory for each backing buffer using the specified size.
     *
     * This method first validates that the buffer's target and usage are valid. If either check fails, it returns an
     * [IllegalArgumentException] with a descriptive message. Otherwise, it binds each backing buffer in turn, allocates its
     * memory storage, and updates to the next buffer before finally unbinding.
     *
     * @param size The desired size for each buffer allocation.
     * @return An [IllegalArgumentException] if validation fails, or null if the allocation succeeds.
     */
    open fun allocate(size: Long): Throwable? {
        if (!bufferValid(target, access))
            return IllegalArgumentException("Target is not valid. Refer to the table in the documentation")

        if (!bufferUsageValid(usage))
            return IllegalArgumentException("Buffer usage is invalid")

        repeat(buffers) {
            bind()
            glBufferData(target, size.coerceAtLeast(0), usage)
            swap()
        }

        bind(0)

        return null
    }

    /**
     * Allocates new storage for the OpenGL buffer using the provided data.
     *
     * This function validates the target and usage before creating storage for each buffer in a multi-buffer
     * configuration. It handles the binding of each buffer and automatically swaps buffers during storage allocation.
     * Note that this operation should only be performed once per buffer.
     *
     * @param data the ByteBuffer containing the data to initialize the buffer storage.
     * @return an IllegalArgumentException for an invalid target or usage, or null if storage allocation is successful.
     */
    open fun storage(data: ByteBuffer): Throwable? {
        if (!bufferValid(target, access))
            return IllegalArgumentException("Target is not valid. Refer to the table in the documentation")

        if (!bufferUsageValid(usage))
            return IllegalArgumentException("Buffer usage is invalid")

        repeat(buffers) {
            bind()
            glBufferStorage(target, data, access or GL_DYNAMIC_STORAGE_BIT)
            swap()
        }

        bind(0)

        return null
    }

    /**
     * Allocates storage for the buffer object.
     *
     * This function initializes storage for each allocated buffer using the specified size,
     * automatically handling binding and unbinding. It validates the buffer's target, access flags,
     * and usage before allocation. Note that it should only be called once per buffer.
     *
     * @param size the desired storage size in bytes (negative values are coerced to zero)
     * @return an IllegalArgumentException if the target or usage is invalid; null if storage allocation succeeds
     */
    open fun storage(size: Long): Throwable? {
        if (!bufferValid(target, access))
            return IllegalArgumentException("Target is not valid. Refer to the table in the documentation")

        if (!bufferUsageValid(usage))
            return IllegalArgumentException("Buffer usage is invalid")

        repeat(buffers) {
            bind()
            glBufferStorage(target, size.coerceAtLeast(0), access or GL_DYNAMIC_STORAGE_BIT)
            swap()
        }

        bind(0)

        return null
    }

    /**
     * Maps a specified region of the buffer's data store into client memory, processes it using the provided lambda, and then unmaps the buffer.
     *
     * The function validates the offset, size, and mapping access flags before mapping the buffer. It then passes the mapped ByteBuffer to the lambda,
     * allowing for direct data manipulation. After the lambda completes, the buffer is unmapped. If any validation or operation fails, a Throwable
     * describing the error is returned; otherwise, null is returned to indicate success.
     *
     * @param size the length of the memory region to map.
     * @param offset the starting offset within the buffer for mapping.
     * @param block a lambda function that receives the mapped ByteBuffer for data manipulation.
     * @return null if the mapping and unmapping succeed; otherwise, a Throwable with the error details.
     */
    open fun map(
        size: Long,
        offset: Long,
        block: (ByteBuffer) -> Unit
    ): Throwable? {
        if (
            offset < 0 ||
            size < 0
        ) return IllegalArgumentException("Invalid offset or size parameter offset: $offset size: $size")

        if (!bufferValid(target, access))
            return IllegalArgumentException("Target is not valid. Refer to the table in the documentation")

        if (!bufferBound(target))
            return IllegalArgumentException("Target is zero bound for glMapBufferRange")

        if (
            offset + size > glGetBufferParameteri(target, GL_BUFFER_SIZE)
        ) return IllegalArgumentException("Out of bound (is the buffer initialized?) $size + $offset > ${glGetBufferParameteri(target, GL_BUFFER_SIZE)}")

        if (
            glGetBufferParameteri(target, GL_BUFFER_MAPPED)
            == GL_TRUE
        ) return IllegalStateException("Buffer is already mapped, something wrong happened")

        if (
            access and GL_MAP_WRITE_BIT == 0 &&
            access and GL_MAP_READ_BIT == 0
        ) return IllegalArgumentException("Neither GL_MAP_READ_BIT nor GL_MAP_WRITE_BIT is set")

        if (
            access and GL_MAP_READ_BIT != 0         &&
            (access and GL_MAP_INVALIDATE_RANGE_BIT == 0 ||
            access and GL_MAP_INVALIDATE_BUFFER_BIT == 0 ||
            access and GL_MAP_UNSYNCHRONIZED_BIT    == 0
            )
        ) return IllegalArgumentException("GL_MAP_READ_BIT is set and any of GL_MAP_INVALIDATE_RANGE_BIT, GL_MAP_INVALIDATE_BUFFER_BIT or GL_MAP_UNSYNCHRONIZED_BIT is set.")

        // Map the buffer into the client's memory
        val sharedRegion = glMapBufferRange(target, offset, size, access)
            ?: return IllegalStateException("Failed to map buffer")

        // Update data on the shared buffer
        block(sharedRegion)

        // Release the buffer
        if (!glUnmapBuffer(target))
            return IllegalStateException("An unknown error occurred due to GPU memory availability of buffer corruption")

        return null
    }

    /**
 * Uploads the specified data to the buffer starting at the given offset.
 *
 * This abstract function should be implemented to perform the actual data transfer into the buffer.
 *
 * @param data   The ByteBuffer containing the data to be uploaded.
 * @param offset The offset within the buffer at which to begin the upload.
 * @return A Throwable if an error occurs during the upload process, or null if the upload is successful.
 */
    abstract fun upload(data: ByteBuffer, offset: Long): Throwable?

    init {
        // Special edge case for vertex arrays
        check(buffers > 0) { "Cannot generate less than one buffer" }

        if (isVertexArray) glGenVertexArrays(bufferIds) // If there are more than 1 buffer you should expect undefined behavior, this is not the way to do it
        else glGenBuffers(bufferIds)
    }
}
