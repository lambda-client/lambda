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

package com.lambda.graphics.buffer.vertex

import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.pipeline.PersistentBuffer
import org.lwjgl.opengl.GL30C.GL_UNSIGNED_INT
import org.lwjgl.opengl.GL30C.glBindVertexArray
import org.lwjgl.opengl.GL30C.glGenVertexArrays
import org.lwjgl.opengl.GL32C.glDrawElementsBaseVertex

class VertexArray(
	private val vertexMode: VertexMode,
	private val attributes: VertexAttrib.Group
) {
	private val vao = glGenVertexArrays()
	private var linkedVBO: PersistentBuffer? = null

	fun renderIndices(
		ibo: PersistentBuffer
	) = linkedVBO?.let { vbo ->
		renderInternal(
			indicesSize = ibo.byteBuffer.bytesPut - ibo.uploadOffset,
			indicesPointer = ibo.byteBuffer.pointer + ibo.uploadOffset,
			verticesOffset = vbo.uploadOffset
		)
	} ?: throw IllegalStateException("Unable to use vertex array without having a VBO linked to it.")

	private fun renderInternal(
		indicesSize: Long,
		indicesPointer: Long,
		verticesOffset: Long
	) {
		glBindVertexArray(vao)
		glDrawElementsBaseVertex(
			vertexMode.mode,
			indicesSize.toInt() / Int.SIZE_BYTES,
			GL_UNSIGNED_INT,
			indicesPointer,
			verticesOffset.toInt() / attributes.stride,
		)
		glBindVertexArray(0)
	}

	fun linkVbo(vbo: PersistentBuffer) {
		linkedVBO = vbo

		glBindVertexArray(vao)
		vbo.buffer.bind { attributes.link() }
		glBindVertexArray(0)
	}
}
