package com.lambda.pathing.launch

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A disc of horizontal velocities.
 *
 * The shape every reachable set in this system takes, which is the whole reason the
 * arithmetic below is possible.
 */
data class VelocityDisc(val x: Double, val z: Double, val radius: Double) {

	fun contains(vx: Double, vz: Double, tolerance: Double = 0.0): Boolean =
		hypot(vx - x, vz - z) <= radius + tolerance

	/**
	 * The range of speeds along a direction this disc can produce. Launch feasibility is
	 * judged on this projection, not the whole vector: lateral drift is the arc's own
	 * tolerance. See docs/decisions/movement-tuning.md (entry reachability).
	 */
	fun projectOnto(unitX: Double, unitZ: Double): ClosedFloatingPointRange<Double> {
		val centre = x * unitX + z * unitZ
		return (centre - radius)..(centre + radius)
	}

	/** Fastest speed in this disc along a bearing, which is what "go that way quickly" means. */
	fun fastestAlong(unitX: Double, unitZ: Double): Pair<Double, Double> =
		(x + unitX * radius) to (z + unitZ * radius)
}

/** A movement input as the planner can actually issue it: a facing and a throttle. */
data class MovementDirective(val yawDegrees: Double, val throttle: Double)

/**
 * Horizontal player movement, treated as what it is: an affine recurrence.
 *
 * Every tick the game computes `v' = friction * (v + acceleration * u)`, where `u` is a
 * unit-or-shorter vector whose direction the player picks freely through yaw and whose
 * length is the throttle. Linear in the velocity, with a disc-shaped control set -- and
 * that combination has consequences worth having in closed form rather than discovering by
 * simulation:
 *
 * - The set of velocities reachable in `n` ticks is exactly a disc, centred on the decayed
 *   entry velocity and with a radius that is a geometric series. [reachable].
 * - Asking whether some velocity is achievable is therefore one distance comparison, and
 *   recovering the input that achieves it is one division. [directiveFor].
 * - "How fast may I take this corner" has an answer rather than a feel. [maxTurnSpeed].
 *
 * The same recurrence governs the air, with a weaker drag and a much smaller acceleration,
 * so the same arithmetic describes mid-flight steering.
 */
class HorizontalDynamics(
	val friction: Double,
	val acceleration: Double,
) {
	init {
		require(friction > 0.0 && friction < 1.0) { "friction must sit in (0, 1): $friction" }
		require(acceleration > 0.0) { "acceleration must be positive: $acceleration" }
	}

	/** The speed this recurrence settles at under continuous full input. */
	val cruise: Double get() = acceleration * friction / (1.0 - friction)

	/** Sum of `friction^k` for k in 1..n -- how much of an input's effect survives to the end. */
	private fun decaySum(ticks: Int): Double =
		if (ticks <= 0) 0.0 else friction * (1.0 - pow(friction, ticks)) / (1.0 - friction)

	private fun pow(base: Double, exponent: Int): Double {
		var result = 1.0
		repeat(exponent) { result *= base }
		return result
	}

	/**
	 * Every velocity reachable from `(vx, vz)` within [ticks], as a disc.
	 *
	 * The centre is the entry velocity decayed by friction -- where the body ends up
	 * holding nothing -- and the radius is what the accumulated input can add in any
	 * direction. Both are exact.
	 */
	fun reachable(vx: Double, vz: Double, ticks: Int): VelocityDisc {
		require(ticks >= 0) { "cannot look backwards: $ticks" }
		val decay = pow(friction, ticks)
		return VelocityDisc(vx * decay, vz * decay, acceleration * decaySum(ticks))
	}

	/** Whether `(toX, toZ)` is reachable from `(fromX, fromZ)` within [ticks]. */
	fun canReach(
		fromX: Double,
		fromZ: Double,
		toX: Double,
		toZ: Double,
		ticks: Int,
		tolerance: Double = 1e-9,
	): Boolean = reachable(fromX, fromZ, ticks).contains(toX, toZ, tolerance)

	/**
	 * The single tick of input that turns `(fromX, fromZ)` into `(toX, toZ)`, or null.
	 *
	 * Inverting the recurrence: `u = (v'/friction - v) / acceleration`. Null when that
	 * asks for more than full throttle, which is the exact feasibility test -- no margin,
	 * no guessing.
	 *
	 * The yaw follows Minecraft's convention, where zero faces positive Z and the movement
	 * direction for a forward press is `(-sin, cos)`.
	 */
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

	/**
	 * The fastest a body may travel and still turn [radians] within [ticks] without losing speed.
	 *
	 * Falls out of asking when a same-speed, rotated velocity still lies inside
	 * [reachable]. Above this speed the turn costs momentum; below it, nothing. It is the
	 * quantity a player carries as instinct -- how hard a corner can be taken flat out --
	 * and it saturates at [cruise] once there are enough ticks to spend.
	 */
	fun maxTurnSpeed(radians: Double, ticks: Int): Double {
		val decay = pow(friction, ticks)
		val spread = sqrt(1.0 + decay * decay - 2.0 * decay * cos(radians))
		if (spread < 1e-12) return cruise
		return acceleration * decaySum(ticks) / spread
	}

	companion object {

		/**
		 * Slipperiness of ordinary ground. Ice and slime differ and would want their own.
		 */
		const val DEFAULT_SLIPPERINESS = Kinematics.DEFAULT_SLIPPERINESS

		const val AIR_DRAG = Kinematics.HORIZONTAL_DRAG

		fun ground(profile: BallisticProfile, sprint: Boolean) = HorizontalDynamics(
			friction = AIR_DRAG * DEFAULT_SLIPPERINESS,
			acceleration = profile.groundAcceleration(sprint),
		)

		fun air(profile: BallisticProfile, sprint: Boolean) = HorizontalDynamics(
			friction = AIR_DRAG,
			acceleration = if (sprint) {
				BallisticProfile.SPRINT_AIR_ACCELERATION
			} else {
				BallisticProfile.WALK_AIR_ACCELERATION
			},
		)

		/** Movement direction for a Minecraft yaw, as `(x, z)`. */
		fun heading(yawDegrees: Double): Pair<Double, Double> {
			val radians = Math.toRadians(yawDegrees)
			return -sin(radians) to cos(radians)
		}
	}
}
