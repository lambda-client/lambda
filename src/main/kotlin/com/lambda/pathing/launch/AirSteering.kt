package com.lambda.pathing.launch

import com.lambda.pathing.core.DEGENERATE_LENGTH
import com.lambda.pathing.core.MovementKeys
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.planarLength
import com.lambda.pathing.physics.MovementSimulationState
import kotlin.math.cos
import kotlin.math.sin

class AirPlan(
	private val aimX: Double,
	private val aimZ: Double,
	private val unitX: Double,
	private val unitZ: Double,
	private val airTicks: Int,
	private val holdTicks: Int,
	private val holdAcceleration: Double,
) {
	fun steer(observed: MovementSimulationState, yawDegrees: Double, airborneTicks: Int): MovementKeys? {
		val remainingTicks = airTicks - airborneTicks + 2
		if (remainingTicks <= 0) return null
		val reach = displacementKernel(remainingTicks)

		val holds = (holdTicks - airborneTicks + 1).coerceIn(0, remainingTicks)
		var plannedAlong = 0.0
		for (slot in 1..holds) plannedAlong += displacementKernel(remainingTicks - slot + 1)
		val plannedX = observed.position.x + observed.velocity.x * reach + holdAcceleration * unitX * plannedAlong
		val plannedZ = observed.position.z + observed.velocity.z * reach + holdAcceleration * unitZ * plannedAlong

		val errorX = aimX - plannedX
		val errorZ = aimZ - plannedZ
		var bestError = planarLength(errorX, errorZ)
		if (bestError <= ON_TARGET_BLOCKS) return null

		val plannedTickX = if (holds >= 1) holdAcceleration * unitX else 0.0
		val plannedTickZ = if (holds >= 1) holdAcceleration * unitZ else 0.0

		val radians = Math.toRadians(yawDegrees)
		val sin = sin(radians)
		val cos = cos(radians)

		var bestForward = 0.0
		var bestStrafe = 0.0
		var improved = false
		for (forward in COMPONENTS) {
			for (strafe in COMPONENTS) {
				var directionX = strafe * cos - forward * sin
				var directionZ = forward * cos + strafe * sin
				val length = planarLength(directionX, directionZ)
				if (length > DEGENERATE_LENGTH) {
					directionX /= length
					directionZ /= length
				}

				val acceleration = when {
					length <= DEGENERATE_LENGTH -> 0.0
					forward > 0.0 -> holdAcceleration
					else -> BallisticProfile.WALK_AIR_ACCELERATION
				}
				val candidateX = errorX - reach * (acceleration * directionX - plannedTickX)
				val candidateZ = errorZ - reach * (acceleration * directionZ - plannedTickZ)
				val error = planarLength(candidateX, candidateZ)
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

	companion object {
		fun toward(from: Stance, to: Stance, solution: LaunchSolution, lateralOffset: Double): AirPlan? {
			val dx = (to.x - from.x).toDouble()
			val dz = (to.z - from.z).toDouble()
			val length = planarLength(dx, dz)
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
				holdAcceleration = if (solution.sprint) BallisticProfile.SPRINT_AIR_ACCELERATION
				else BallisticProfile.WALK_AIR_ACCELERATION,
			)
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
}
