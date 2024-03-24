package com.lambda.util.math

import java.awt.Color

object ColorUtils {
    fun Color.setAlpha(value: Double) =
        Color(red, green, blue, (value * 255.0).coerceIn(0.0, 255.0).toInt())

    fun Color.multAlpha(value: Double) =
        Color(red, green, blue, (value * alpha).coerceIn(0.0, 255.0).toInt())

    val Color.r get() = red.toDouble() / 255.0
    val Color.g get() = green.toDouble() / 255.0
    val Color.b get() = blue.toDouble() / 255.0
    val Color.a get() = alpha.toDouble() / 255.0
}