package com.lambda.util.math

import java.awt.Color

val Color.hsb get() = Color.RGBtoHSB(red, green, blue, null)
    .map(Float::toDouble)
    .toDoubleArray()

fun DoubleArray.readHSB() =
    Color.getHSBColor(this[0].toFloat(), this[1].toFloat(), this[2].toFloat())

val Color.hue get() = hsb[0]
val Color.saturation get() = hsb[1]
val Color.brightness get() = hsb[2]

val Color.r get() = red / 255f
val Color.g get() = green / 255f
val Color.b get() = blue / 255f
val Color.a get() = alpha / 255f