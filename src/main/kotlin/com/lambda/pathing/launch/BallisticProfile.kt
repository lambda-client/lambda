package com.lambda.pathing.launch

import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.physics.Vanilla

enum class LaunchMode(val sprint: Boolean, val jumps: Boolean) {
	SPRINT_JUMP(sprint = true, jumps = true),
	WALK_JUMP(sprint = false, jumps = true),
	SPRINT_DROP(sprint = true, jumps = false),
	WALK_DROP(sprint = false, jumps = false),
	;

	val drops: Boolean get() = !jumps

	fun supports(rise: Double): Boolean = if (jumps) rise <= MAX_JUMP_RISE else rise < 0.0

	companion object {

		const val MAX_JUMP_RISE = 1.25

		const val MAX_JUMP_STEP = 2
	}
}

class ArcSample(
	val heights: DoubleArray,
	val distances: DoubleArray,
	val exitSpeed: Double,
) {
	val airTicks: Int get() = heights.size - 1

	val distance: Double get() = distances[distances.size - 1]

	val apex: Double get() = heights.max()
}

data class BallisticProfile(
	val gravity: Double,
	val jumpVelocity: Double,
	val walkGroundAcceleration: Double,
	val sprintGroundAcceleration: Double,
	val slipperiness: Double,
) {
	val groundFriction: Double = (slipperiness.toFloat() * Vanilla.HORIZONTAL_DRAG).toDouble()

	private val walkMomentumSpeed = momentumOf(sprint = false)
	private val sprintMomentumSpeed = momentumOf(sprint = true)

	fun momentumSpeed(sprint: Boolean): Double = if (sprint) sprintMomentumSpeed else walkMomentumSpeed

	val jumpApex: Double = generateSequence(jumpVelocity) { (it - gravity) * VERTICAL_DRAG }.takeWhile { it > 0.0 }.sum()

	private fun momentumOf(sprint: Boolean): Double {
		val cruise = cruiseSpeed(sprint)
		val landing = fly(if (sprint) LaunchMode.SPRINT_JUMP else LaunchMode.WALK_JUMP, cruise, 0.0) ?: return cruise
		return maxOf(cruise, landing.exitSpeed)
	}

	fun fallStep(fallSpeed: Double): Double = (fallSpeed + gravity) * VERTICAL_DRAG

	val coastDistancePerSpeed: Double get() = 1.0 / (1.0 - groundFriction)

	val climbSpeed: Double get() = (Vanilla.CLIMB_RISE_SPEED - gravity) * VERTICAL_DRAG

	fun cruiseDisplacement(sprint: Boolean): Double = groundAcceleration(sprint) / (1.0 - groundFriction)

	fun groundAcceleration(sprint: Boolean): Double =
		if (sprint) sprintGroundAcceleration else walkGroundAcceleration

	fun cruiseSpeed(sprint: Boolean): Double {
		val acceleration = groundAcceleration(sprint)
		return groundFriction * acceleration / (1.0 - groundFriction)
	}

	fun groundSpeedReach(
		velocityX: Double,
		velocityZ: Double,
		unitX: Double,
		unitZ: Double,
		ticks: Int,
		sprint: Boolean,
	): ClosedFloatingPointRange<Double> {
		var decay = 1.0
		repeat(ticks) { decay *= groundFriction }
		val centre = velocityX * decay * unitX + velocityZ * decay * unitZ
		val radius = groundAcceleration(sprint) * (if (ticks <= 0) 0.0 else groundFriction * (1.0 - decay) / (1.0 - groundFriction))
		return (centre - radius)..(centre + radius)
	}

	fun runUpSpeed(entrySpeed: Double, ticks: Int, sprint: Boolean): Double {
		var velocity = entrySpeed
		repeat(ticks) { velocity = (velocity + groundAcceleration(sprint)) * groundFriction }
		return velocity
	}

	fun groundReachable(
		target: ClosedFloatingPointRange<Double>,
		ticks: Int,
		sprint: Boolean,
	): ClosedFloatingPointRange<Double> {
		require(ticks >= 0) { "a ground phase cannot be negative: $ticks" }

		var high = target.endInclusive
		repeat(ticks) { high /= groundFriction }

		val low = if (runUpSpeed(0.0, ticks, sprint) >= target.start) 0.0 else target.start
		return low..high.coerceAtMost(momentumSpeed(sprint))
	}

	fun runUpDistanceFor(targetSpeed: Double, sprint: Boolean, maxTicks: Int = 24): Double? {
		var velocity = 0.0
		var covered = 0.0
		repeat(maxTicks) {
			val step = velocity + groundAcceleration(sprint)
			covered += step
			velocity = step * groundFriction
			if (velocity >= targetSpeed) return covered
		}
		return null
	}

	fun groundRunUpTicks(
		entrySpeed: Double,
		distance: Double,
		sprint: Boolean,
		maxTicks: Int,
	): Int {
		if (distance <= 0.0) return 0
		val acceleration = groundAcceleration(sprint)
		var velocity = entrySpeed
		var covered = 0.0
		for (tick in 0 until maxTicks) {
			val step = velocity + acceleration
			covered += step
			velocity = step * groundFriction
			if (covered >= distance) return tick + 1
		}
		return maxTicks
	}

	fun fly(
		mode: LaunchMode,
		entrySpeed: Double,
		rise: Double,
		holdForward: Boolean = true,
		maxTicks: Int = MAX_ARC_TICKS,
		holdTicks: Int = Int.MAX_VALUE,
		headroom: Double = Double.POSITIVE_INFINITY,
	): ArcSample? = arc(entrySpeed, rise, holdForward, mode.sprint, mode.jumps, holdTicks, maxTicks, headroom = headroom)

	fun bounce(
		entrySpeed: Double,
		drop: Double,
		rise: Double,
		holdForward: Boolean = false,
		sprint: Boolean = false,
		jump: Boolean = false,
		holdTicks: Int = Int.MAX_VALUE,
		bounceFactor: Double = 1.0,
		maxTicks: Int = MAX_BOUNCE_TICKS,
		headroom: Double = Double.POSITIVE_INFINITY,
	): ArcSample? {
		require(drop > 0.0) { "a bounce must fall onto something: drop=$drop" }
		return arc(entrySpeed, rise, holdForward, sprint, jump, holdTicks, maxTicks, drop, bounceFactor, headroom)
	}

	private fun arc(
		entrySpeed: Double,
		rise: Double,
		holdForward: Boolean,
		sprint: Boolean,
		jump: Boolean,
		holdTicks: Int,
		maxTicks: Int,
		drop: Double = Double.NaN,
		bounceFactor: Double = 1.0,
		headroom: Double = Double.POSITIVE_INFINITY,
	): ArcSample? {
		require(maxTicks in 0 until Int.MAX_VALUE) { "invalid arc tick limit: $maxTicks" }
		val heights = DoubleArray(maxTicks + 1)
		val distances = DoubleArray(maxTicks + 1)
		var velocity = entrySpeed
		var verticalVelocity = 0.0
		var height = 0.0
		var distance = 0.0
		var floor = -drop
		var grounded = false

		if (jump) {
			verticalVelocity = jumpVelocity
			if (sprint) velocity += SPRINT_JUMP_BOOST
		}
		if (holdForward && holdTicks > 0) velocity += groundAcceleration(sprint)
		distance += velocity
		if (verticalVelocity > 0.0 && height + verticalVelocity > headroom) {
			height = headroom
			verticalVelocity = 0.0
		} else {
			height += verticalVelocity
		}
		verticalVelocity = (verticalVelocity - gravity) * VERTICAL_DRAG
		velocity *= groundFriction
		heights[0] = height
		distances[0] = distance

		val airAcceleration = when {
			!holdForward -> 0.0
			sprint -> SPRINT_AIR_ACCELERATION
			else -> WALK_AIR_ACCELERATION
		}
		for (tick in 1..maxTicks) {
			val wasGrounded = grounded
			grounded = false
			when {
				wasGrounded && holdForward && tick < holdTicks -> velocity += groundAcceleration(sprint)
				tick < holdTicks -> velocity += airAcceleration
				!drop.isNaN() -> velocity += 0.0
			}
			distance += velocity

			if (height + verticalVelocity <= floor) {
				height = floor
				verticalVelocity = -verticalVelocity * bounceFactor
				floor = Double.NaN
				grounded = true
			} else {
				if (floor.isNaN() && verticalVelocity < 0.0) {
					if (height < rise) return null
					if (height + verticalVelocity <= rise) return landedSample(tick, rise, distance, velocity, heights, distances)
				}
				if (verticalVelocity > 0.0 && height + verticalVelocity > headroom) {
					height = headroom
					verticalVelocity = 0.0
				} else {
					height += verticalVelocity
				}
			}

			verticalVelocity = (verticalVelocity - gravity) * VERTICAL_DRAG
			velocity *= if (wasGrounded) groundFriction else HORIZONTAL_DRAG
			heights[tick] = height
			distances[tick] = distance
		}
		return null
	}

	private fun landedSample(
		tick: Int,
		rise: Double,
		distance: Double,
		velocity: Double,
		heights: DoubleArray,
		distances: DoubleArray,
	): ArcSample {
		heights[tick] = rise
		distances[tick] = distance
		return ArcSample(heights.copyOf(tick + 1), distances.copyOf(tick + 1), velocity * HORIZONTAL_DRAG)
	}

	companion object {

		const val MAX_BOUNCE_TICKS = 64

		const val MAX_ARC_TICKS = 24

		private const val SPRINT_JUMP_BOOST = Vanilla.SPRINT_JUMP_BOOST

		private const val VERTICAL_DRAG = Vanilla.VERTICAL_DRAG.toDouble()
		const val HORIZONTAL_DRAG = Vanilla.HORIZONTAL_DRAG.toDouble()

		private const val INPUT_DAMPING = Vanilla.INPUT_DAMPING.toDouble()

		const val WALK_AIR_ACCELERATION = INPUT_DAMPING * Vanilla.AIR_ACCELERATION
		const val SPRINT_AIR_ACCELERATION = INPUT_DAMPING * Vanilla.SPRINT_AIR_ACCELERATION

		private const val DEFAULT_SLIPPERINESS = Vanilla.DEFAULT_SLIPPERINESS.toDouble()

		val VANILLA: BallisticProfile = PlayerPhysicsProfile.VANILLA.let { profile ->
			val slip = DEFAULT_SLIPPERINESS.toFloat()
			val scale = Vanilla.GROUND_ACCELERATION_SCALE / (slip * slip * slip)
			fun acceleration(speed: Double): Double = INPUT_DAMPING * (speed.toFloat() * scale)
			BallisticProfile(
				gravity = profile.gravity,
				jumpVelocity = (profile.jumpStrength.toFloat() + profile.jumpBoostVelocityModifier.toFloat()).toDouble(),
				walkGroundAcceleration = acceleration(profile.movementSpeed),
				sprintGroundAcceleration = acceleration(profile.movementSpeed * Vanilla.SPRINT_SPEED_MULTIPLIER),
				slipperiness = DEFAULT_SLIPPERINESS,
			)
		}
	}
}
