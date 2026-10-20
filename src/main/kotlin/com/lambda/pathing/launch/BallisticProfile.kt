package com.lambda.pathing.launch

import com.lambda.pathing.core.DEGENERATE_LENGTH
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.world.Footing
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.hypot

enum class LaunchMode(val sprint: Boolean, val jumps: Boolean) {
	SPRINT_JUMP(sprint = true, jumps = true),
	WALK_JUMP(sprint = false, jumps = true),
	SPRINT_DROP(sprint = true, jumps = false),
	WALK_DROP(sprint = false, jumps = false),
	;

	val drops: Boolean get() = !jumps

	fun supports(rise: Double): Boolean = if (jumps) rise <= MAX_JUMP_RISE else rise < 0.0

	companion object {

		const val MAX_JUMP_RISE = Footing.JUMP_RISE

		const val MAX_JUMP_STEP = 2
	}
}

private val NO_CONTACTS = IntArray(0)

fun interface HorizontalClip {
	fun allowed(alongX: Boolean, x: Double, z: Double, delta: Double, tick: Int): Double
}

class ArcSample(
	val heights: DoubleArray,
	val distances: DoubleArray,
	val exitSpeed: Double,
	val contacts: IntArray = NO_CONTACTS,
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

	private val probeArcs = ConcurrentHashMap<ArcKey, Any>()

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

	fun groundAcceleration(sprint: Boolean, slipperiness: Float): Double {
		val default = this.slipperiness.toFloat()
		return groundAcceleration(sprint) * (default * default * default) / (slipperiness * slipperiness * slipperiness)
	}

	fun groundFriction(slipperiness: Float): Double = (slipperiness * Vanilla.HORIZONTAL_DRAG).toDouble()

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
		bounceFactor: Double = Vanilla.SLIME_BOUNCE_FACTOR,
		bounces: Int = 1,
		maxTicks: Int = MAX_BOUNCE_TICKS * bounces,
		headroom: Double = Double.POSITIVE_INFINITY,
		reboundHeadroom: Double = Double.POSITIVE_INFINITY,
	): ArcSample? {
		require(drop > 0.0) { "a bounce must fall onto something: drop=$drop" }
		require(bounces >= 1) { "a bounce touches at least one pad: bounces=$bounces" }
		return arc(entrySpeed, rise, holdForward, sprint, jump, holdTicks, maxTicks, drop, bounceFactor, headroom, bounces, reboundHeadroom)
	}

	fun bounce(
		entrySpeed: Double,
		depths: DoubleArray,
		rise: Double,
		holdForward: Boolean = false,
		sprint: Boolean = false,
		jump: Boolean = false,
		holdTicks: Int = Int.MAX_VALUE,
		maxTicks: Int = MAX_BOUNCE_TICKS * depths.size,
		headroom: Double = Double.POSITIVE_INFINITY,
		reboundHeadroom: Double = Double.POSITIVE_INFINITY,
	): ArcSample? {
		require(depths.isNotEmpty() && depths.all { it > 0.0 }) { "a bounce must fall onto something: depths=${depths.contentToString()}" }
		return arc(entrySpeed, rise, holdForward, sprint, jump, holdTicks, maxTicks, depths[0], 1.0, headroom, depths.size, reboundHeadroom, depths)
	}

	fun steeredPath(
		entrySpeed: Double,
		sprint: Boolean,
		jump: Boolean,
		contacts: IntArray,
		ticks: Int,
		unitX: Double,
		unitZ: Double,
		turnAt: Int,
		targetX: Double,
		targetZ: Double,
		contactAcceleration: Double = groundAcceleration(sprint),
		contactFriction: Double = groundFriction,
		clip: HorizontalClip? = null,
	): DoubleArray {
		val path = DoubleArray(2 * (ticks + 1))
		val ground = groundAcceleration(sprint)
		val air = if (sprint) SPRINT_AIR_ACCELERATION else WALK_AIR_ACCELERATION
		var velocityX = entrySpeed * unitX
		var velocityZ = entrySpeed * unitZ
		if (jump && sprint) {
			velocityX += SPRINT_JUMP_BOOST * unitX
			velocityZ += SPRINT_JUMP_BOOST * unitZ
		}
		velocityX += ground * unitX
		velocityZ += ground * unitZ
		var x = 0.0
		var z = 0.0
		fun move(tick: Int) {
			val xFirst = abs(velocityX) >= abs(velocityZ)
			repeat(2) { pass ->
				val alongX = (pass == 0) == xFirst
				val delta = if (alongX) velocityX else velocityZ
				val allowed = clip?.allowed(alongX, x, z, delta, tick) ?: delta
				if (alongX) {
					x += allowed
					if (allowed != delta) velocityX = 0.0
				} else {
					z += allowed
					if (allowed != delta) velocityZ = 0.0
				}
			}
		}
		move(0)
		velocityX *= groundFriction
		velocityZ *= groundFriction
		path[0] = x
		path[1] = z
		var contact = 0
		var grounded = false
		for (tick in 1..ticks) {
			val wasGrounded = grounded
			grounded = contact < contacts.size && contacts[contact] == tick
			if (grounded) contact++
			var directionX = unitX
			var directionZ = unitZ
			if (tick >= turnAt) {
				val towardX = targetX - x
				val towardZ = targetZ - z
				val length = hypot(towardX, towardZ)
				if (length > DEGENERATE_LENGTH) {
					directionX = towardX / length
					directionZ = towardZ / length
				}
			}
			val acceleration = if (wasGrounded) contactAcceleration else air
			velocityX += acceleration * directionX
			velocityZ += acceleration * directionZ
			move(tick)
			val drag = if (wasGrounded) contactFriction else HORIZONTAL_DRAG
			velocityX *= drag
			velocityZ *= drag
			path[2 * tick] = x
			path[2 * tick + 1] = z
		}
		return path
	}

	private data class ArcKey(
		val entrySpeed: Double,
		val rise: Double,
		val holdForward: Boolean,
		val sprint: Boolean,
		val jump: Boolean,
		val holdTicks: Int,
		val maxTicks: Int,
		val drop: Double,
		val bounceFactor: Double,
		val headroom: Double,
		val bounces: Int,
		val reboundHeadroom: Double,
		val floors: List<Double>?,
	) {
		override fun hashCode(): Int {
			var hash = 0L
			fun add(bits: Long) {
				hash = (hash xor bits) * GOLDEN_GAMMA
				hash = hash xor (hash ushr 29)
			}
			add(entrySpeed.toBits())
			add(rise.toBits())
			add((if (holdForward) 1L else 0L) or (if (sprint) 2L else 0L) or (if (jump) 4L else 0L))
			add(holdTicks.toLong() shl 32 or (maxTicks.toLong() and 0xFFFFFFFFL))
			add(drop.toBits())
			add(bounceFactor.toBits())
			add(headroom.toBits())
			add(bounces.toLong())
			add(reboundHeadroom.toBits())
			add(floors?.hashCode()?.toLong() ?: -1L)
			return (hash xor (hash ushr 32)).toInt()
		}

		override fun equals(other: Any?): Boolean = other is ArcKey &&
				entrySpeed.compareTo(other.entrySpeed) == 0 && rise.compareTo(other.rise) == 0 &&
				holdForward == other.holdForward && sprint == other.sprint && jump == other.jump &&
				holdTicks == other.holdTicks && maxTicks == other.maxTicks && drop.compareTo(other.drop) == 0 &&
				bounceFactor.compareTo(other.bounceFactor) == 0 && headroom.compareTo(other.headroom) == 0 &&
				bounces == other.bounces && reboundHeadroom.compareTo(other.reboundHeadroom) == 0 && floors == other.floors

		private companion object {
			const val GOLDEN_GAMMA = -0x61c8864680b583ebL
		}
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
		bounceFactor: Double = Vanilla.SLIME_BOUNCE_FACTOR,
		headroom: Double = Double.POSITIVE_INFINITY,
		bounces: Int = 1,
		reboundHeadroom: Double = Double.POSITIVE_INFINITY,
		floors: DoubleArray? = null,
	): ArcSample? {
		if (entrySpeed != 0.0 && entrySpeed != 1.0) return simulate(entrySpeed, rise, holdForward, sprint, jump, holdTicks, maxTicks, drop, bounceFactor, headroom, bounces, reboundHeadroom, floors)
		val key = ArcKey(entrySpeed, rise, holdForward, sprint, jump, holdTicks, maxTicks, drop, bounceFactor, headroom, bounces, reboundHeadroom, floors?.toList())
		probeArcs[key]?.let { return it as? ArcSample }
		val arc = simulate(entrySpeed, rise, holdForward, sprint, jump, holdTicks, maxTicks, drop, bounceFactor, headroom, bounces, reboundHeadroom, floors)
		if (probeArcs.size >= PROBE_ARC_LIMIT) probeArcs.clear()
		probeArcs[key] = arc ?: NO_ARC
		return arc
	}

	private fun simulate(
		entrySpeed: Double,
		rise: Double,
		holdForward: Boolean,
		sprint: Boolean,
		jump: Boolean,
		holdTicks: Int,
		maxTicks: Int,
		drop: Double,
		bounceFactor: Double,
		headroom: Double,
		bounces: Int,
		reboundHeadroom: Double,
		floors: DoubleArray?,
	): ArcSample? {
		require(maxTicks in 0 until Int.MAX_VALUE) { "invalid arc tick limit: $maxTicks" }
		val heights = DoubleArray(maxTicks + 1)
		val distances = DoubleArray(maxTicks + 1)
		var velocity = entrySpeed
		var verticalVelocity = 0.0
		var height = 0.0
		var distance = 0.0
		var floor = -drop
		var contacts = 0
		val contactTicks = if (drop.isNaN()) null else IntArray(bounces)
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

			if (height >= floor && height + verticalVelocity <= floor) {
				height = floor
				verticalVelocity = -verticalVelocity * bounceFactor
				contactTicks?.set(contacts, tick)
				contacts++
				floor = when {
					contacts == bounces -> Double.NaN
					floors != null -> -floors[contacts]
					else -> floor
				}
				grounded = true
			} else {
				if (floor.isNaN() && verticalVelocity < 0.0) {
					if (height < rise) return null
					if (height + verticalVelocity <= rise) return landedSample(tick, rise, distance, velocity, heights, distances, contactTicks?.copyOf(contacts) ?: NO_CONTACTS)
				}
				val ceiling = if (contacts > 0 || floor.isNaN()) minOf(headroom, reboundHeadroom) else headroom
				if (verticalVelocity > 0.0 && height + verticalVelocity > ceiling) {
					height = ceiling
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
		contacts: IntArray,
	): ArcSample {
		heights[tick] = rise
		distances[tick] = distance
		return ArcSample(heights.copyOf(tick + 1), distances.copyOf(tick + 1), velocity * HORIZONTAL_DRAG, contacts)
	}

	companion object {

		private const val PROBE_ARC_LIMIT = 1 shl 15

		private val NO_ARC = Any()

		const val MAX_BOUNCE_TICKS = 64

		const val MAX_ARC_TICKS = 24

		private const val SPRINT_JUMP_BOOST = Vanilla.SPRINT_JUMP_BOOST

		private const val VERTICAL_DRAG = Vanilla.VERTICAL_DRAG.toDouble()
		const val HORIZONTAL_DRAG = Vanilla.HORIZONTAL_DRAG.toDouble()

		private const val INPUT_DAMPING = Vanilla.INPUT_DAMPING.toDouble()

		const val WALK_AIR_ACCELERATION = INPUT_DAMPING * Vanilla.AIR_ACCELERATION
		const val SPRINT_AIR_ACCELERATION = INPUT_DAMPING * Vanilla.SPRINT_AIR_ACCELERATION

		val VANILLA: BallisticProfile = PlayerPhysicsProfile.VANILLA.let { profile ->
			val slip = SnapshotBlockPhysics.DEFAULT_SLIPPERINESS.toFloat()
			val scale = Vanilla.GROUND_ACCELERATION_SCALE / (slip * slip * slip)
			fun acceleration(speed: Double): Double = INPUT_DAMPING * (speed.toFloat() * scale)
			BallisticProfile(
				gravity = profile.gravity,
				jumpVelocity = (profile.jumpStrength.toFloat() + profile.jumpBoostVelocityModifier.toFloat()).toDouble(),
				walkGroundAcceleration = acceleration(profile.movementSpeed),
				sprintGroundAcceleration = acceleration(profile.movementSpeed * Vanilla.SPRINT_SPEED_MULTIPLIER),
				slipperiness = SnapshotBlockPhysics.DEFAULT_SLIPPERINESS,
			)
		}
	}
}
