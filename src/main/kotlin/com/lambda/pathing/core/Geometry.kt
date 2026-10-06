package com.lambda.pathing.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class HorizontalPoint(val x: Double, val y: Double, val z: Double)

const val DEGENERATE_LENGTH = 1e-9

fun Stance.center() = HorizontalPoint(x + 0.5, y.toDouble(), z + 0.5)

fun horizontalDistance(from: HorizontalPoint, to: HorizontalPoint): Double =
	hypot(to.x - from.x, to.z - from.z)

fun bearingBetween(from: HorizontalPoint, to: HorizontalPoint): Double =
	Math.toDegrees(atan2(to.z - from.z, to.x - from.x)) - 90.0

fun headingOfBearing(bearing: Double): Pair<Double, Double> {
	val radians = Math.toRadians(bearing + 90.0)
	return cos(radians) to sin(radians)
}

fun alongEdge(from: HorizontalPoint, to: HorizontalPoint, x: Double, z: Double): Double {
	val dx = to.x - from.x
	val dz = to.z - from.z
	val length = hypot(dx, dz)
	if (length <= DEGENERATE_LENGTH) return 0.0
	return ((x - from.x) * dx + (z - from.z) * dz) / length
}

fun unitBetween(from: HorizontalPoint, to: HorizontalPoint): Pair<Double, Double>? {
	val length = hypot(to.x - from.x, to.z - from.z)
	if (length <= DEGENERATE_LENGTH) return null
	return (to.x - from.x) / length to (to.z - from.z) / length
}

fun interpolate(from: HorizontalPoint, to: HorizontalPoint, distance: Double): HorizontalPoint {
	val length = horizontalDistance(from, to)
	if (length <= DEGENERATE_LENGTH) return from
	val scale = distance / length
	return HorizontalPoint(
		from.x + (to.x - from.x) * scale,
		from.y,
		from.z + (to.z - from.z) * scale,
	)
}

fun headingAlignment(headingX: Double, headingZ: Double, towardX: Double, towardZ: Double): Double {
	val headingLength = hypot(headingX, headingZ)
	val distance = hypot(towardX, towardZ)
	if (headingLength <= DEGENERATE_LENGTH || distance <= DEGENERATE_LENGTH) return 0.0
	return (headingX * towardX + headingZ * towardZ) / (headingLength * distance)
}
