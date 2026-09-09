package com.lambda.pathing.launch

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

data class VelocityDisc(val x: Double, val z: Double, val radius: Double) {

	fun contains(vx: Double, vz: Double, tolerance: Double = 0.0): Boolean =
		hypot(vx - x, vz - z) <= radius + tolerance

	fun projectOnto(unitX: Double, unitZ: Double): ClosedFloatingPointRange<Double> {
		val centre = x * unitX + z * unitZ
		return (centre - radius)..(centre + radius)
	}

	fun fastestAlong(unitX: Double, unitZ: Double): Pair<Double, Double> =
		(x + unitX * radius) to (z + unitZ * radius)
}

data class MovementDirective(val yawDegrees: Double, val throttle: Double)

class HorizontalDynamics(
	val friction: Double,
	val acceleration: Double,
) {
	init {
		require(friction > 0.0 && friction < 1.0) { "friction must sit in (0, 1): $friction" }
		require(acceleration > 0.0) { "acceleration must be positive: $acceleration" }
	}

	val cruise: Double get() = acceleration * friction / (1.0 - friction)

	private fun decaySum(ticks: Int): Double =
		if (ticks <= 0) 0.0 else friction * (1.0 - pow(friction, ticks)) / (1.0 - friction)

	private fun pow(base: Double, exponent: Int): Double {
		var result = 1.0
		repeat(exponent) { result *= base }
		return result
	}

	fun reachable(vx: Double, vz: Double, ticks: Int): VelocityDisc {
		require(ticks >= 0) { "cannot look backwards: $ticks" }
		val decay = pow(friction, ticks)
		return VelocityDisc(vx * decay, vz * decay, acceleration * decaySum(ticks))
	}

	fun canReach(
		fromX: Double,
		fromZ: Double,
		toX: Double,
		toZ: Double,
		ticks: Int,
		tolerance: Double = 1e-9,
	): Boolean = reachable(fromX, fromZ, ticks).contains(toX, toZ, tolerance)

	fun directiveFor(
		fromX: Double,
		fromZ: Double,
		toX: Double,
		toZ: Double,
	): MovementDirective? {
		val ux = (toX / friction - fromX) / acceleration
		val uz = (toZ / friction - fromZ) / acceleration
		val throttle = hypot(ux, uz)
		if (throttle > 1.0 + 1e-9) return null
		if (throttle < 1e-12) return MovementDirective(Math.toDegrees(atan2(-fromX, fromZ)), 0.0)
		return MovementDirective(Math.toDegrees(atan2(-ux, uz)), throttle.coerceAtMost(1.0))
	}

	fun maxTurnSpeed(radians: Double, ticks: Int): Double {
		val decay = pow(friction, ticks)
		val spread = sqrt(1.0 + decay * decay - 2.0 * decay * cos(radians))
		if (spread < 1e-12) return cruise
		return acceleration * decaySum(ticks) / spread
	}

	companion object {

		const val DEFAULT_SLIPPERINESS = Kinematics.DEFAULT_SLIPPERINESS

		const val AIR_DRAG = Kinematics.HORIZONTAL_DRAG

		fun ground(profile: BallisticProfile, sprint: Boolean) = HorizontalDynamics(
			friction = AIR_DRAG * DEFAULT_SLIPPERINESS,
			acceleration = profile.groundAcceleration(sprint),
		)

		fun air(sprint: Boolean) = HorizontalDynamics(
			friction = AIR_DRAG,
			acceleration = if (sprint) {
				BallisticProfile.SPRINT_AIR_ACCELERATION
			} else {
				BallisticProfile.WALK_AIR_ACCELERATION
			},
		)

		fun heading(yawDegrees: Double): Pair<Double, Double> {
			val radians = Math.toRadians(yawDegrees)
			return -sin(radians) to cos(radians)
		}
	}
}
