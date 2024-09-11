package com.lambda.util.math

import net.minecraft.util.math.Vec3d
import java.awt.Color

val Color.hsb
    get() = Color.RGBtoHSB(red, green, blue, null)
        .map(Float::toDouble)

fun DoubleArray.readHSB(): Color = Color.getHSBColor(this[0].toFloat(), this[1].toFloat(), this[2].toFloat())

val Color.hue get() = hsb[0]
val Color.saturation get() = hsb[1]
val Color.brightness get() = hsb[2]

fun Color.setAlpha(value: Double) =
    Color(red, green, blue, (value * 255.0).coerceIn(0.0, 255.0).toInt())

fun Color.multAlpha(value: Double) =
    Color(red, green, blue, (value * alpha).coerceIn(0.0, 255.0).toInt())

val Color.r get() = red / 255.0
val Color.g get() = green / 255.0
val Color.b get() = blue / 255.0
val Color.a get() = alpha / 255.0

val Color.vec3d get() = Vec3d(r, g, b)
