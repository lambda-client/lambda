package com.lambda.graphics.buffer

import com.lambda.graphics.gl.bufferValid
import com.lambda.graphics.gl.bufferBound
import com.lambda.graphics.gl.bufferUsageValid
import org.lwjgl.opengl.GL30C.*
import java.nio.ByteBuffer

interface IBuffer {
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
    val buffers: Int

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
    val usage: Int

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
    val target: Int

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
    val access: Int

    /**
     * Index of the current buffer
     */
    var index: Int

    /**
     * List of all the buffers
     */
    val bufferIds: IntArray

    /**
     * Binds the buffer id to the [target]
     */
    fun bind(id: Int) = glBindBuffer(target, id)

    /**
     * Binds current the buffer [index] to the [target]
     */
    fun bind() = bind(bufferIds[index])

    /**
     * Swaps the buffer [index] if [buffers] is greater than 1
     */
    fun swap() { index = (index + 1) % buffers }

    /**
     * Update the current buffer without re-allocating
     * Alternative to [map]
     */
    fun update(
        data:   ByteBuffer,
        offset: Long,
    ): Throwable? {
        if(!bufferValid(target))
            return IllegalArgumentException("Target is not valid. Refer to the table in the documentation")

        if (!bufferBound(target))
            return IllegalArgumentException("Target is zero bound for glBufferSubData")

        glBufferSubData(target, offset, data)

        return null
    }

    /**
     * Grows the backing buffers
     * This function should not be called frequently
     *
     * @param size The size of the new buffer
     */
    fun grow(size: Long): Throwable? {
        if(
            size    < 0
        ) return IllegalArgumentException("Invalid size parameter: $size")

        // FixMe: If access contains any of GL_MAP_PERSISTENT_BIT or GL_MAP_COHERENT_BIT and the buffer was not initialized using glBufferStorage, glMapBufferRange will fail
        if(!bufferValid(target))
            return IllegalArgumentException("Target is not valid. Refer to the table in the documentation")

        if (!bufferUsageValid(usage))
            return IllegalArgumentException("Buffer usage is invalid")

        bufferIds.forEach { bufferId ->
            // Orphan the buffer and allocate a new one
            bind(bufferId)

            // Only resize if the new size is bigger than the bound buffer capacity
            if (size > glGetBufferParameteri(target, GL_BUFFER_SIZE))
                glBufferData(target, size, usage)

            bind(0) // Don't forget to unbind to avoid accidental buffer modification
        }

        return null
    }

    /**
     * Maps all or part of a buffer object's data store into the client's address space
     *
     * @param offset    Specifies the starting offset within the buffer of the range to be mapped.
     * @param size      Specifies the length of the range to be mapped.
     * @param block     Lambda scope with the mapped buffer passed in
     * @return          Error encountered during the mapping process
     */
    fun map(
        offset: Long,
        size:   Long,
        block:  (ByteBuffer) -> Unit
    ): Throwable? {
        if(
            offset < 0 ||
            size   < 0
        ) return IllegalArgumentException("Invalid offset or size parameter offset: $offset size: $size")

        if(!bufferValid(target))
            return IllegalArgumentException("Target is not valid. Refer to the table in the documentation")

        if (!bufferBound(target))
            return IllegalArgumentException("Target is zero bound for glMapBufferRange")

        if(
            offset + size > glGetBufferParameteri(target, GL_BUFFER_SIZE)
        ) return IllegalArgumentException("Out of bound mapping: $offset + $size > ${glGetBufferParameteri(target, GL_BUFFER_SIZE)}")

        if(
            glGetBufferParameteri(target, GL_BUFFER_MAPPED)
            == GL_TRUE
        ) return IllegalStateException("Buffer is already mapped, something wrong happened")

        if(
            access and GL_MAP_WRITE_BIT == 0 &&
            access and GL_MAP_READ_BIT  == 0
        ) return IllegalArgumentException("Neither GL_MAP_READ_BIT nor GL_MAP_WRITE_BIT is set")

        if(
            access and GL_MAP_READ_BIT               != 0 &&
            (access and GL_MAP_INVALIDATE_RANGE_BIT  == 0 ||
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
     * Sets the given data into the client mapped memory and executes the provided processing function to manage data transfer.
     *
     * @param data      Data to set in memory
     * @param offset    The starting offset within the buffer of the range to be mapped
     * @return          Error encountered during the mapping process
     */
    fun upload(data: ByteArray, offset: Long): Throwable? =
        upload(ByteBuffer.wrap(data), offset)

    /**
     * Sets the given data into the client mapped memory and executes the provided processing function to manage data transfer.
     *
     * @param data      Data to set in memory
     * @param offset    The starting offset within the buffer of the range to be mapped
     * @return          Error encountered during the mapping process
     */
    fun upload(data: ByteBuffer, offset: Long): Throwable?
}
