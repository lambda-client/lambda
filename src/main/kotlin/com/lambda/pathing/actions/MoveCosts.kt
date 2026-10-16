package com.lambda.pathing.actions

import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.BounceSolver
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.physics.Vanilla
import kotlin.math.max
import kotlin.math.sqrt

object CoarseMoveRates {
	val SPRINT_BLOCKS_PER_TICK = BallisticProfile.VANILLA.cruiseDisplacement(sprint = true)

	fun floorSlowdown(velocityMultiplier: Double): Double {
		if (velocityMultiplier >= 1.0) return 1.0
		val friction = BallisticProfile.VANILLA.groundFriction
		return (1.0 - friction * velocityMultiplier) / (1.0 - friction)
	}

	val SPRINT_JUMP_BLOCKS_PER_TICK: Double get() = MeasuredMotion.sprintJumpBlocksPerTick

	const val FREE_STEP_RISE = Vanilla.STEP_HEIGHT.toDouble()

	const val MIN_JUMP_DISTANCE = 2.0

	val CLIMB_TICKS_PER_BLOCK = 1.0 / BallisticProfile.VANILLA.climbSpeed

	val SWIM_BLOCKS_PER_TICK: Double get() = MeasuredMotion.swimBlocksPerTick

	fun waterExitTicks(rise: Int): Double = MeasuredMotion.waterExitTicks(rise)

	private const val MAX_FALL_TABLE_DEPTH = 12

	private val FALL_TICKS = DoubleArray(MAX_FALL_TABLE_DEPTH + 1).also { table ->
		var velocity = 0.0
		var fallen = 0.0
		var tick = 0
		var depth = 1
		while (depth <= MAX_FALL_TABLE_DEPTH) {
			tick++
			velocity = BallisticProfile.VANILLA.fallStep(velocity)
			fallen += velocity
			while (depth <= MAX_FALL_TABLE_DEPTH && fallen >= depth) {
				table[depth] = tick.toDouble()
				depth++
			}
		}
	}

	fun fallTicks(depth: Int): Double = FALL_TICKS[depth.coerceIn(1, MAX_FALL_TABLE_DEPTH)]
}

class CoarseMoveCosts(
	val cardinalWalk: Double,
	val diagonalWalk: Double,
	val stepUp: Double,
	val walkOff: (depth: Int) -> Double,
	val jumpCandidate: (distance: Double, verticalOffset: Int) -> Double?,
	val drop: (distance: Double, depth: Int) -> Double? = { _, depth -> walkOff(depth) },
	val swim: Double = 1.0 / CoarseMoveRates.SWIM_BLOCKS_PER_TICK,
	val diagonalSwim: Double = sqrt(2.0) * swim,
	val waterExit: (rise: Int) -> Double = CoarseMoveRates::waterExitTicks,
) {
	init {
		validate("cardinalWalk", cardinalWalk)
		validate("diagonalWalk", diagonalWalk)
		validate("stepUp", stepUp)
		validate("swim", swim)
		validate("diagonalSwim", diagonalSwim)
	}

	fun jumpCandidateCost(distance: Double, verticalOffset: Int): Double? {
		require(distance >= CoarseMoveRates.MIN_JUMP_DISTANCE || verticalOffset < 0 || verticalOffset >= LaunchMode.MAX_JUMP_STEP) {
			"a level jump candidate must reach past the adjacent stance: $distance"
		}
		return jumpCandidate(distance, verticalOffset)?.also {
			validate("jumpCandidate($distance, $verticalOffset)", it)
		}
	}

	fun walkOffCost(depth: Int): Double {
		require(depth > 0) { "walk-off depth must be positive" }
		return walkOff(depth).also { validate("walkOff($depth)", it) }
	}

	fun bounceCost(drop: Int, rise: Int, distance: Double, bounces: Int = 1, ledgeDepth: Double = 0.0): Double? {
		require(drop > 0) { "a bounce must fall onto something: $drop" }
		return BounceSolver.ticks(drop, rise, distance, bounces, ledgeDepth)?.toDouble()
	}

	fun dropCost(distance: Double, depth: Int): Double? {
		require(distance >= 1.0) { "a drop must cross at least the adjacent stance" }
		require(depth > 0) { "drop depth must be positive" }
		return drop(distance, depth)?.also { validate("drop($distance, $depth)", it) }
	}

	private fun validate(name: String, value: Double) {
		require(value.isFinite() && value > 0.0) { "$name must be finite and positive: $value" }
	}

	companion object {

		fun measured(): CoarseMoveCosts {
			val profile = BallisticProfile.VANILLA
			val walk = 1.0 / CoarseMoveRates.SPRINT_BLOCKS_PER_TICK

			fun ballistic(jumping: Boolean, rise: Int): Double? {
				val height = minOf(rise.toDouble(), LaunchMode.MAX_JUMP_RISE)
				return LaunchMode.entries
					.filter { it.jumps == jumping && it.supports(height) }
					.mapNotNull { mode -> profile.fly(mode, profile.cruiseSpeed(mode.sprint), height)?.airTicks }
					.minOrNull()?.let { (it + 1).toDouble() }
			}

			fun jump(horizontalDistance: Double, verticalOffset: Int): Double? =
				ballistic(jumping = true, rise = verticalOffset)?.let { max(it, horizontalDistance / CoarseMoveRates.SPRINT_JUMP_BLOCKS_PER_TICK) }

			val step = checkNotNull(ballistic(jumping = true, rise = 1)) { "no jump reaches a block up" }

			return CoarseMoveCosts(
				cardinalWalk = walk,
				diagonalWalk = sqrt(2.0) * walk,
				stepUp = step,
				walkOff = { depth -> max(walk, CoarseMoveRates.fallTicks(depth)) },
				jumpCandidate = { distance, vo -> jump(distance, vo) },
				swim = 1.0 / CoarseMoveRates.SWIM_BLOCKS_PER_TICK,
				diagonalSwim = sqrt(2.0) / CoarseMoveRates.SWIM_BLOCKS_PER_TICK,
				waterExit = { rise -> CoarseMoveRates.waterExitTicks(rise) },
				drop = { distance, depth ->
					ballistic(jumping = false, rise = -depth)?.let { max(it, distance / CoarseMoveRates.SPRINT_BLOCKS_PER_TICK) }
				},
			)
		}
	}
}
