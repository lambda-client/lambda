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

import org.lwjgl.BufferUtils
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.memPutByte
import org.lwjgl.system.MemoryUtil.memPutFloat
import org.lwjgl.system.MemoryUtil.memPutInt
import java.awt.Color
import java.nio.Buffer
import java.nio.ByteBuffer

object Memory {
    /**
     * Puts a float for each axis at the current buffer position
     */
    fun vector2f(address: Long, x: Double, y: Double): Int {
        float(address + 0, x)
        float(address + 4, y)
        return 8
    }

    /**
     * Puts a float for each axis at the current buffer position
     */
    fun vector3f(address: Long, x: Double, y: Double, z: Double): Int {
        float(address + 0, x)
        float(address + 4, y)
        float(address + 8, z)
        return 12
    }

    /**
     * Puts a byte for each color channel at the current buffer position
     */
    fun color(address: Long, color: Color): Int {
        byte(address + 0, color.red.toByte())
        byte(address + 1, color.green.toByte())
        byte(address + 2, color.blue.toByte())
        byte(address + 3, color.alpha.toByte())
        return 4
    }

    /**
     * Puts a byte at the current buffer position
     */
    fun byte(address: Long, value: Byte): Int {
        memPutByte(address, value)
        return 1
    }

    /**
     * Puts an integer at the current buffer position
     */
    fun int(address: Long, value: Int): Int {
        memPutInt(address, value)
        return 4
    }

    /**
     * Puts a float at the current buffer position
     */
    fun float(address: Long, value: Double): Int {
        memPutFloat(address, value.toFloat())
        return 4
    }

    /**
     * Returns the address of the first element within the buffer
     */
    fun address(buffer: Buffer): Long = MemoryUtil.memAddress0(buffer)

    /**
     * Copies [bytes] bytes from [src] to [dst]
     */
    fun copy(src: Long, dst: Long, bytes: Long) = MemoryUtil.memCopy(src, dst, bytes)

    /**
     * Creates a new buffer of [cap] bytes
     */
    fun byteBuffer(cap: Int) = BufferUtils.createByteBuffer(cap)
}

val Int.kilobyte get() = this * 1000
val Int.megabyte get() = this * 1000 * 1000
val Int.gigabyte get() = this * 1000 * 1000 * 1000

val Int.kibibyte get() = this * 1024
val Int.mebibyte get() = this * 1024 * 1024
val Int.gibibyte get() = this * 1024 * 1024 * 1024

val Long.kilobyte get() = this * 1000
val Long.megabyte get() = this * 1000 * 1000
val Long.gigabyte get() = this * 1000 * 1000 * 1000

val Long.kibibyte get() = this * 1024
val Long.mebibyte get() = this * 1024 * 1024
val Long.gibibyte get() = this * 1024 * 1024 * 1024

/**
 * Returns memory alignment for each CPU architecture
 */
fun alignment(): Int {
    return when (System.getProperty("os.arch")?.lowercase()) {
        "x86", "x86_64" -> 4  // 32-bit or 64-bit x86
        "arm", "armv7l", "aarch64" -> 4  // ARM architectures
        else -> 8  // Default to 8 bytes alignment for other architectures
    }
}

/**
 * Returns how many bytes will be added to reach memory alignment
 */
fun padding(size: Int): Int = size % alignment() / 8

fun ByteBuffer.putTo(dst: ByteBuffer) { dst.put(this) }
