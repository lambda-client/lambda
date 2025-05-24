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

package com.lambda.util

class VarIntIterator(
    private val bytes: ByteArray,
) : Iterator<Int> {
    private var index: Int = 0

    override fun hasNext(): Boolean = index < bytes.size

    override fun next(): Int {
        if (!hasNext())
            throw NoSuchElementException("No more elements to read")

        var value = 0
        var size = 0

        do {
            val b = bytes[index++].toInt()
            value = value or ((b and SEGMENT_BIT) shl (size++ * 7))

            if (size > 5) throw IllegalArgumentException("VarInt size cannot exceed 5 bytes")
        } while ((b and CONTINUE_BIT) != 0)

        return value
    }

    companion object {
        const val SEGMENT_BIT = 127
        const val CONTINUE_BIT = 128
    }
}

inline fun ByteArray.varIterator(block: (Int) -> Unit) =
    VarIntIterator(this).forEach(block)
