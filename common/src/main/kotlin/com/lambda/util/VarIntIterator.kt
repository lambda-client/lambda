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

package com.lambda.util

class VarIntIterator(
    private val bytes: ByteArray,
    private val bitsPerEntry: Int = 7,
    private val maxGroups: Int = 5,
) : Iterator<Int> {
    private var index: Int = 0

    override fun hasNext(): Boolean = index < bytes.size

    override fun next(): Int {
        if (!hasNext())
            throw NoSuchElementException("No more elements to read")

        var value = 0
        var bitsRead = 0

        val groupMask = (1 shl bitsPerEntry) - 1
        val continuationBit = 1 shl bitsPerEntry

        var b: Byte
        do {
            if (index >= bytes.size)
                throw NoSuchElementException("Unexpected end of byte array while reading VarInt")

            b = bytes[index++]
            value = value or ((b.toInt() and groupMask) shl bitsRead)
            bitsRead += bitsPerEntry

            require(bitsRead <= bitsPerEntry * maxGroups) { "VarInt size cannot exceed $maxGroups bytes" }
        } while ((b.toInt() and continuationBit) != 0)

        return value
    }
}
