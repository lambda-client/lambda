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

import com.lambda.graphics.buffer.Buffer
import com.lambda.graphics.buffer.DynamicByteBuffer.Companion.dynamicByteBuffer
import com.lambda.graphics.gl.kibibyte
import org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT
import org.lwjgl.opengl.GL44.GL_DYNAMIC_STORAGE_BIT
import org.lwjgl.system.MemoryUtil.memCopy

/**
 * Represents a persistent dynamic coherent buffer for fast opengl rendering purposes
 */
class PersistentBuffer(
	target: Int, stride: Int, initialSize: Int = 1.kibibyte
) {
	/**
	 * Resizable byte buffer that stores all data used last frame
	 */
	val byteBuffer = dynamicByteBuffer(stride * initialSize)

	/**
	 * Represents a OpenGl Object that store unformatted memory
	 */
	val buffer = Buffer.create(target, GL_MAP_WRITE_BIT or GL_DYNAMIC_STORAGE_BIT) { allocate(byteBuffer.capacity.toLong()) }

	/**
	 * Data that has passed through the buffer within previous frame
	 */
	private val snapshot = dynamicByteBuffer(1)
	private var snapshotData = 0L

	private var glSize = 0

	var uploadOffset = 0L

	fun upload() {
		val dataStart = byteBuffer.pointer + uploadOffset
		val dataCount = byteBuffer.bytesPut - uploadOffset
		if (dataCount <= 0) return

		if (glSize != byteBuffer.capacity) {
			glSize = byteBuffer.capacity
			buffer.allocate(byteBuffer.data)
			snapshot.realloc(byteBuffer.capacity)
			snapshotData = 0
			return
		}

		if (snapshotData > 0 && snapshot.capacity >= byteBuffer.bytesPut) {
			if (snapshot.mismatch(byteBuffer) >= 0) return
		}

		buffer.update(uploadOffset, dataCount, dataStart)
	}

	fun end() {
		uploadOffset = byteBuffer.bytesPut
	}

	fun sync() {
		memCopy(byteBuffer.pointer, snapshot.pointer, byteBuffer.bytesPut)
		snapshotData = byteBuffer.bytesPut

		byteBuffer.resetPosition()
		uploadOffset = 0
	}

	fun clear() {
		snapshot.resetPosition()
		byteBuffer.resetPosition()
		uploadOffset = 0
		snapshotData = 0
	}
}
