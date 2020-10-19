package me.zeroeightsix.kami.util.color

object ColorConverter {
    @JvmStatic
    fun toF(i: Int): Float {
        return i / 255f
    }

    @JvmStatic
    fun toF(d: Double): Float {
        return (d / 255f).toFloat()
    }

    @JvmStatic
    fun rgbToHex(r: Int, g: Int, b: Int, a: Int): Int {
        return r shl 16 or (g shl 8) or b or (a shl 24)
    }

    @JvmStatic
    fun rgbToHex(r: Int, g: Int, b: Int): Int {
        return r shl 16 or (g shl 8) or b
    }

    @JvmStatic
    fun hexToRgb(hexColor: Int): ColorHolder {
        val r = hexColor shr 16 and 255
        val g = hexColor shr 8 and 255
        val b = hexColor and 255
        return ColorHolder(r, g, b)
    }
}