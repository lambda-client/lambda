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

import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import org.lwjgl.BufferUtils
import org.lwjgl.system.MemoryUtil
import java.awt.Color
import java.nio.Buffer
import java.nio.ByteBuffer

object Memory {
    private val floatSize = VertexAttrib.Float.size
    private val vec2Size = VertexAttrib.Vec2.size
    private val vec3Size = VertexAttrib.Vec3.size
    private val colorSize = VertexAttrib.Color.size

    fun vec2(address: Long, x: Double, y: Double): Int {
        float(address + 0, x)
        float(address + 4, y)
        return vec2Size
    }

    fun vec3(address: Long, x: Double, y: Double, z: Double): Int {
        float(address + 0, x)
        float(address + 4, y)
        float(address + 8, z)
        return vec3Size
    }

    fun color(address: Long, color: Color): Int {
        byte(address + 0, color.red.toByte())
        byte(address + 1, color.green.toByte())
        byte(address + 2, color.blue.toByte())
        byte(address + 3, color.alpha.toByte())
        return colorSize
    }

    private fun byte(address: Long, value: Byte) {
        MemoryUtil.memPutByte(address, value)
    }

    fun int(address: Long, value: Int) {
        MemoryUtil.memPutInt(address, value)
    }

    fun float(address: Long, value: Double): Int {
        MemoryUtil.memPutFloat(address, value.toFloat())
        return floatSize
    }

    fun address(buffer: Buffer): Long {
        return MemoryUtil.memAddress0(buffer)
    }

    fun copy(from: Long, to: Long, bytes: Long) {
        MemoryUtil.memCopy(from, to, bytes)
    }

    fun byteBuffer(cap: Int): ByteBuffer {
        return BufferUtils.createByteBuffer(cap)
    }

    val Buffer.capacity get() = capacity()
}
