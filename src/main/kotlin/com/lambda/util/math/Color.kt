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

package com.lambda.util.math

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
