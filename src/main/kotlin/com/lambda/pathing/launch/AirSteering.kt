package com.lambda.pathing.launch

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object AirSteering {
	data class Keys(val forward: Double, val strafe: Double)

	data class AirPlan(
		val aimX: Double,
		val aimZ: Double,
		val unitX: Double,
		val unitZ: Double,
		val airTicks: Int,
		val holdTicks: Int,
		val sprintAcceleration: Double,
		val walkAcceleration: Double,
	)

	fun steer(
		positionX: Double,
		positionZ: Double,
		velocityX: Double,
		velocityZ: Double,
		yawDegrees: Double,
		aimX: Double,
		aimZ: Double,
		unitX: Double,
		unitZ: Double,
		remainingTicks: Int,
		plannedHoldTicks: Int,
		sprintAcceleration: Double,
		walkAcceleration: Double,
	): Keys? {
		if (remainingTicks <= 0) return null
		val reach = displacementKernel(remainingTicks)

		val holds = plannedHoldTicks.coerceIn(0, remainingTicks)
		var plannedAlong = 0.0
		for (slot in 1..holds) plannedAlong += displacementKernel(remainingTicks - slot + 1)
		val plannedX = positionX + velocityX * reach + sprintAcceleration * unitX * plannedAlong
		val plannedZ = positionZ + velocityZ * reach + sprintAcceleration * unitZ * plannedAlong

		val errorX = aimX - plannedX
		val errorZ = aimZ - plannedZ
		if (hypot(errorX, errorZ) <= ON_TARGET_BLOCKS) return null

		val plannedTickX = if (holds >= 1) sprintAcceleration * unitX else 0.0
		val plannedTickZ = if (holds >= 1) sprintAcceleration * unitZ else 0.0

		val radians = Math.toRadians(yawDegrees)
		val sin = sin(radians)
		val cos = cos(radians)

		var bestForward = if (holds >= 1) 1.0 else 0.0
		var bestStrafe = 0.0
		var bestError = hypot(errorX, errorZ)
		var improved = false
		for (forward in COMPONENTS) {
			for (strafe in COMPONENTS) {

				var directionX = strafe * cos - forward * sin
				var directionZ = forward * cos + strafe * sin
				val length = hypot(directionX, directionZ)
				if (length > 1e-9) {
					directionX /= length
					directionZ /= length
				}

				val acceleration = when {
					length <= 1e-9 -> 0.0
					forward > 0.0 -> sprintAcceleration
					else -> walkAcceleration
				}
				val candidateX = errorX - reach * (acceleration * directionX - plannedTickX)
				val candidateZ = errorZ - reach * (acceleration * directionZ - plannedTickZ)
				val error = hypot(candidateX, candidateZ)
				if (error < bestError - 1e-9) {
					bestError = error
					bestForward = forward
					bestStrafe = strafe
					improved = true
				}
			}
		}
		return if (improved) Keys(bestForward, bestStrafe) else null
	}

	private fun displacementKernel(ticks: Int): Double {
		var sum = 0.0
		var factor = 1.0
		repeat(ticks) {
			sum += factor
			factor *= HorizontalDynamics.AIR_DRAG
		}
		return sum
	}

	private const val ON_TARGET_BLOCKS = 0.1

	private val COMPONENTS = doubleArrayOf(-1.0, 0.0, 1.0)
}
