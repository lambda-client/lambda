
package com.minato.gui.snap

data class RectF(val x: Float, val y: Float, val w: Float, val h: Float) {
    val left get() = x
    val right get() = x + w
    val top get() = y
    val bottom get() = y + h
    val cx get() = x + w * 0.5f
    val cy get() = y + h * 0.5f
}