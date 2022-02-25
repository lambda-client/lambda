package com.lambda.client.commons.extension

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor

const val PI_FLOAT = 3.14159265358979323846f

fun Double.floorToInt() = floor(this).toInt()

fun Float.floorToInt() = floor(this).toInt()

fun Double.ceilToInt() = ceil(this).toInt()

fun Float.ceilToInt() = ceil(this).toInt()

fun Float.toRadian() = this / 180.0f * PI_FLOAT

fun Double.toRadian() = this / 180.0 * PI

fun Float.toDegree() = this * 180.0f / PI_FLOAT

fun Double.toDegree() = this * 180.0 / PI