
package com.minato.util.math

import net.minecraft.util.math.Vec3d
import java.awt.Color

fun Color.setAlpha(value: Int) =
    Color(red, green, blue, value.coerceIn(0, 255))

fun Color.setAlpha(value: Double) =
    Color(red, green, blue, (value * 255.0).coerceIn(0.0, 255.0).toInt())

fun Color.multAlpha(value: Double) =
    Color(red, green, blue, (value * alpha).coerceIn(0.0, 255.0).toInt())

val Color.r get() = red / 255.0
val Color.g get() = green / 255.0
val Color.b get() = blue / 255.0
val Color.a get() = alpha / 255.0

val Color.vec3d get() = Vec3d(r, g, b)
