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

import com.lambda.graphics.buffer.Buffer.Companion.createPipelineBuffer
import com.lambda.graphics.buffer.DynamicByteBuffer.Companion.dynamicByteBuffer
import com.lambda.graphics.gl.kibibyte
import org.lwjgl.system.MemoryUtil.memCopy
import java.nio.ByteBuffer

/**
 * Represents a persistent dynamic coherent buffer for fast opengl rendering purposes
 */
class PersistentBuffer(
    target: Int, stride: Int
) {
    /**
     * Resizable byte buffer that stores all data used last frame
     */
    val byteBuffer = dynamicByteBuffer(stride * 1.kibibyte)

    /**
     * Data that has passed through the buffer within previous frame
     */
    private val snapshot = dynamicByteBuffer(1)
    private var cacheSize = 0

    /**
     * Represents a gpu-side buffer
     */
    private val glBuffer = createPipelineBuffer(target)
    private var glSize = 0

    var uploadOffset = 0

    fun upload() {
        val dataStart = byteBuffer.pointer + uploadOffset
        val dataCount = byteBuffer.bytesPut - uploadOffset

        if (glSize != byteBuffer.capacity) {
            glSize = byteBuffer.capacity

            glBuffer.allocate(byteBuffer.data)
            snapshot.realloc(byteBuffer.capacity)
            cacheSize = 0
            return

            /* TODO:
                Cache data in range min(snapshot.capacity, byteBuffer.bytesPut)
                and force upload after byteBuffer.bytesPut
            */
        } else if (cacheSize > 0 && snapshot.capacity >= byteBuffer.bytesPut) {
            // TODO: precise compare-mapping to minimize uploaded data
            // Split data by chunks of modified regions and upload them only
            // Might be useful in cases when position updates but uv/color/etc doesn't
            // Might be not...
            if (memcmp(snapshot.data, byteBuffer.data, uploadOffset, dataCount.toInt())) return
        }

        glBuffer.update(uploadOffset.toLong(), dataCount, dataStart)
        println(dataCount)
    }

    fun end() {
        uploadOffset = byteBuffer.bytesPut.toInt()
    }

    fun sync() {
        memCopy(byteBuffer.pointer, snapshot.pointer, byteBuffer.bytesPut)
        cacheSize = byteBuffer.bytesPut.toInt()

        byteBuffer.resetPosition()
        uploadOffset = 0
    }

    fun clear() {
        snapshot.resetPosition()
        byteBuffer.resetPosition()
        uploadOffset = 0
        cacheSize = 0
    }

    fun use(block: () -> Unit) {
        glBuffer.bind()
        block()
        glBuffer.bind(0)
    }

    private fun memcmp(a: ByteBuffer, b: ByteBuffer, pointer: Int, size: Int): Boolean {
        for (i in pointer..<(pointer + size)) {
            if (a[i] != b[i]) {
                return false
            }
        }
        return true
    }
}
