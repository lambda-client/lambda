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

package com.lambda.graphics.gl

import org.lwjgl.opengl.GL44.*

/**
 * Map of valid buffer binding target to their respective binding check parameter
 * using [bufferBound]
 */
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

/**
 * Returns whether the buffer target is valid
 */
fun bufferValid(target: Int): Boolean = target in bindingCheckMappings

/**
 * Returns whether the provided buffer target is bound
 */
fun bufferBound(target: Int): Boolean =
    IntArray(1)
        .apply { glGetIntegerv(bindingCheckMappings.getValue(target), this) }[0] != GL_FALSE

/**
 * Returns whether the provided buffer usage is valid
 */
fun bufferUsageValid(usage: Int) = usage >= GL_STREAM_DRAW && usage <= GL_DYNAMIC_DRAW
