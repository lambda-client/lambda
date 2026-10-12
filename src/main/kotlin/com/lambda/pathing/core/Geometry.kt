package com.lambda.pathing.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

data class HorizontalPoint(val x: Double, val y: Double, val z: Double)

data class Via(val x: Double, val z: Double) {
	fun detour(dx: Int, dz: Int, landingShift: Double = 0.0): Double {
		val length = hypot(dx.toDouble(), dz.toDouble())
		val toX = dx - dz / length * landingShift
		val toZ = dz + dx / length * landingShift
		return hypot(x, z) + hypot(toX - x, toZ - z) - length
	}
}

operator fun HorizontalPoint.plus(via: Via) = copy(x = x + via.x, z = z + via.z)

const val DEGENERATE_LENGTH = 1e-9

fun planarLength(x: Double, z: Double): Double = sqrt(x * x + z * z)

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
	val headingLength = planarLength(headingX, headingZ)
	val distance = planarLength(towardX, towardZ)
	if (headingLength <= DEGENERATE_LENGTH || distance <= DEGENERATE_LENGTH) return 0.0
	return (headingX * towardX + headingZ * towardZ) / (headingLength * distance)
}
