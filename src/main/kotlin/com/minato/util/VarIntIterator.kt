
package com.minato.util

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
