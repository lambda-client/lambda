package com.lambda.pathing.launch

data class VelocityDisc(val x: Double, val z: Double, val radius: Double) {

	fun projectOnto(unitX: Double, unitZ: Double): ClosedFloatingPointRange<Double> {
		val centre = x * unitX + z * unitZ
		return (centre - radius)..(centre + radius)
	}
}

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

	companion object {

		fun ground(profile: BallisticProfile, sprint: Boolean) = HorizontalDynamics(
			friction = profile.groundFriction,
			acceleration = profile.groundAcceleration(sprint),
		)
	}
}
