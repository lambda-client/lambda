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

import org.lwjgl.BufferUtils.createByteBuffer
import org.lwjgl.system.MemoryUtil.memAddress0
import org.lwjgl.system.MemoryUtil.memCopy
import org.lwjgl.system.MemoryUtil.memPutByte
import org.lwjgl.system.MemoryUtil.memPutFloat
import org.lwjgl.system.MemoryUtil.memPutInt
import java.awt.Color
import java.nio.ByteBuffer

/**
 * Dynamically resizable byte buffer designed for efficient vertex building.
 * Automatically grows capacity when needed, and provides
 * convenience methods for common data types used in vertex attributes.
 *
 * @property data The underlying [java.nio.ByteBuffer] storing the vertex data
 * @property capacity Current maximum capacity of the buffer in bytes
 * @property pointer Base memory address of the buffer
 * @property position Current write position in memory address space
 */
class DynamicByteBuffer private constructor(initialCapacity: Int) {
    var data: ByteBuffer = createByteBuffer(initialCapacity); private set
    var capacity = initialCapacity; private set

    var pointer = memAddress0(data); private set
    var position = pointer; private set

    /**
     * Gets the total number of bytes written to the buffer since last reset
     */
    val bytesPut get() = position - pointer

    /**
     * Resets the write position to the beginning of the buffer while maintaining current capacity,
     * allowing for buffer reuse without reallocation
     */
    fun resetPosition() {
        position = pointer
    }

    /**
     * Writes a single byte value at the current position
     * @param value The byte value to write
     */
    fun putByte(value: Byte) {
        require(1)
        memPutByte(position, value)
        position += 1
    }

    /**
     * Writes a 4-byte integer value at the current position
     * @param value The integer value to write
     */
    fun putInt(value: Int) {
        require(4)
        memPutInt(position, value)
        position += 4
    }

    /**
     * Writes a 4-byte floating point value at the current position
     * @param value The double-precision value to write (will be converted to float)
     */
    fun putFloat(value: Double) {
        require(4)
        memPutFloat(position, value.toFloat())
        position += 4
    }

    /**
     * Writes a 2-component vector as two consecutive 4-byte floats
     * @param x X-axis component
     * @param y Y-axis component
     */
    fun putVec2(x: Double, y: Double) {
        require(8)
        memPutFloat(position + 0, x.toFloat())
        memPutFloat(position + 4, y.toFloat())
        position += 8
    }

    /**
     * Writes a 3-component vector as three consecutive 4-byte floats
     * @param x X-axis component
     * @param y Y-axis component
     * @param z Z-axis component
     */
    fun putVec3(x: Double, y: Double, z: Double) {
        require(12)
        memPutFloat(position + 0, x.toFloat())
        memPutFloat(position + 4, y.toFloat())
        memPutFloat(position + 8, z.toFloat())
        position += 12
    }

    /**
     * Writes a color as four consecutive bytes in RGBA format
     * @param color The color to write
     */
    fun putColor(color: Color) {
        require(4)
        memPutByte(position + 0, color.red.toByte())
        memPutByte(position + 1, color.green.toByte())
        memPutByte(position + 2, color.blue.toByte())
        memPutByte(position + 3, color.alpha.toByte())
        position += 4
    }

    /**
     * Ensures the buffer has enough remaining space for the requested number of bytes.
     * Automatically grows the buffer if insufficient space remains.
     * @param size Number of bytes required for the next write operation
     */
    fun require(size: Int) {
        if (capacity - bytesPut > size) return
        grow(capacity * 2)
    }

    /**
     * Increases buffer capacity while preserving existing data. New capacity must be greater than current.
     * @param newCapacity New buffer capacity in bytes
     * @throws IllegalArgumentException if newCapacity is not greater than current capacity
     */
    fun grow(newCapacity: Int) {
        check(newCapacity > capacity) {
            "Cannot grow buffer beyond its capacity"
        }

        val newBuffer = createByteBuffer(newCapacity)
        val newPointer = memAddress0(newBuffer)
        val offset = position - pointer

        memCopy(pointer, newPointer, offset)

        data = newBuffer
        pointer = newPointer
        position = newPointer + offset
        capacity = newCapacity
    }

    /**
     * Reallocates the buffer with exact new capacity, resetting position and discarding existing data
     * @param newCapacity New buffer capacity in bytes
     */
    fun realloc(newCapacity: Int) {
        if (newCapacity == capacity) {
            resetPosition()
            return
        }

        val newBuffer = createByteBuffer(newCapacity)
        val newPointer = memAddress0(newBuffer)

        data = newBuffer
        pointer = newPointer
        position = newPointer
        capacity = newCapacity
    }

    /**
     * Returns the relative index of the first mismatch between this and the given buffer, otherwise -1 if no mismatch.
     *
     * @see [ByteBuffer.mismatch]
     */
    fun mismatch(other: DynamicByteBuffer) = data.mismatch(other.data)

    companion object {
        /**
         * Creates a new DynamicByteBuffer with specified initial capacity
         * @param initialCapacity Starting buffer size in bytes
         */
        fun dynamicByteBuffer(initialCapacity: Int) =
            DynamicByteBuffer(initialCapacity)
    }
}
