package com.lambda.pathing.launch

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import com.lambda.pathing.core.DEGENERATE_LENGTH
import com.lambda.pathing.core.MovementKeys
import com.lambda.pathing.core.Stance

object AirSteering {
	data class AirPlan(
		val aimX: Double,
		val aimZ: Double,
		val unitX: Double,
		val unitZ: Double,
		val airTicks: Int,
		val holdTicks: Int,
		val sprintAcceleration: Double,
	) {
		companion object {
			fun toward(from: Stance, to: Stance, solution: LaunchSolution, lateralOffset: Double): AirPlan? {
				val dx = (to.x - from.x).toDouble()
				val dz = (to.z - from.z).toDouble()
				val length = hypot(dx, dz)
				if (length <= DEGENERATE_LENGTH) return null
				val unitX = dx / length
				val unitZ = dz / length
				return AirPlan(
					aimX = to.x + 0.5 - unitZ * lateralOffset,
					aimZ = to.z + 0.5 + unitX * lateralOffset,
					unitX = unitX,
					unitZ = unitZ,
					airTicks = solution.airTicks,
					holdTicks = solution.heldForwardTicks,
					sprintAcceleration = if (solution.sprint) BallisticProfile.SPRINT_AIR_ACCELERATION
					else BallisticProfile.WALK_AIR_ACCELERATION,
				)
			}
		}
	}

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
	): MovementKeys? {
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
				if (length > DEGENERATE_LENGTH) {
					directionX /= length
					directionZ /= length
				}

				val acceleration = when {
					length <= DEGENERATE_LENGTH -> 0.0
					forward > 0.0 -> sprintAcceleration
					else -> BallisticProfile.WALK_AIR_ACCELERATION
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
		return if (improved) MovementKeys(bestForward, bestStrafe) else null
	}

	private fun displacementKernel(ticks: Int): Double {
		var sum = 0.0
		var factor = 1.0
		repeat(ticks) {
			sum += factor
			factor *= BallisticProfile.HORIZONTAL_DRAG
		}
		return sum
	}

	private const val ON_TARGET_BLOCKS = 0.1

	private val COMPONENTS = doubleArrayOf(-1.0, 0.0, 1.0)
}
