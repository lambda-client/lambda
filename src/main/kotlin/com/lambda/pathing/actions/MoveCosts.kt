package com.lambda.pathing.actions

import com.lambda.pathing.launch.BallisticProfile
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

	const val SPRINT_JUMP_BLOCKS_PER_TICK = 0.3640

	const val STEP_UP_BLOCKS_PER_TICK = 0.1000

	const val FREE_STEP_RISE = Vanilla.STEP_HEIGHT

	const val MIN_JUMP_DISTANCE = 2.0

	val CLIMB_TICKS_PER_BLOCK = 1.0 / BallisticProfile.VANILLA.climbSpeed

	const val SWIM_BLOCKS_PER_TICK = 0.129

	private const val BANK_EXIT_TICKS = 11.0

	private const val BEACH_EXIT_TICKS = 9.0

	fun waterExitTicks(rise: Int): Double = if (rise > 0) BANK_EXIT_TICKS else BEACH_EXIT_TICKS

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
	val jumpCandidate: (distance: Double, verticalOffset: Int) -> Double,
	val drop: (distance: Double, depth: Int) -> Double = { _, depth -> walkOff(depth) },
	val cornerWalk: Double = diagonalWalk,
	val swim: Double = 1.0 / CoarseMoveRates.SWIM_BLOCKS_PER_TICK,
	val diagonalSwim: Double = sqrt(2.0) * swim,
	val waterExit: (rise: Int) -> Double = CoarseMoveRates::waterExitTicks,
) {
	init {
		validate("cardinalWalk", cardinalWalk)
		validate("diagonalWalk", diagonalWalk)
		validate("cornerWalk", cornerWalk)
		validate("stepUp", stepUp)
		validate("swim", swim)
		validate("diagonalSwim", diagonalSwim)
	}

	fun jumpCandidateCost(distance: Double, verticalOffset: Int): Double {
		require(distance >= CoarseMoveRates.MIN_JUMP_DISTANCE || verticalOffset < 0 || verticalOffset >= LaunchMode.MAX_JUMP_STEP) {
			"a level jump candidate must reach past the adjacent stance: $distance"
		}
		return jumpCandidate(distance, verticalOffset).also {
			validate("jumpCandidate($distance, $verticalOffset)", it)
		}
	}

	fun walkOffCost(depth: Int): Double {
		require(depth > 0) { "walk-off depth must be positive" }
		return walkOff(depth).also { validate("walkOff($depth)", it) }
	}

	fun bounceCost(drop: Int, rise: Int, bounces: Int = 1): Double {
		require(drop > 0) { "a bounce must fall onto something: $drop" }
		return CoarseMoveRates.fallTicks(drop) * (2 * bounces - 1) + CoarseMoveRates.fallTicks(drop + rise) + BOUNCE_SETTLE_TICKS
	}

	fun dropCost(distance: Double, depth: Int): Double {
		require(distance >= 1.0) { "a drop must cross at least the adjacent stance" }
		require(depth > 0) { "drop depth must be positive" }
		return drop(distance, depth).also { validate("drop($distance, $depth)", it) }
	}

	private fun validate(name: String, value: Double) {
		require(value.isFinite() && value > 0.0) { "$name must be finite and positive: $value" }
	}

	companion object {

		private const val UNREACHABLE_BALLISTIC_TICKS = 1000.0

		private const val DROP_SETTLE_TICKS = 2.0

		private const val BOUNCE_SETTLE_TICKS = 2.0

		private const val JUMP_SETTLE_TICKS = 0.5

		fun measured(
			transitionOverheadTicks: Double = 0.0,
		): CoarseMoveCosts {
			val profile = BallisticProfile.VANILLA
			require(transitionOverheadTicks >= 0.0 && transitionOverheadTicks.isFinite()) {
				"transitionOverheadTicks must be non-negative and finite: $transitionOverheadTicks"
			}
			val walk = 1.0 / CoarseMoveRates.SPRINT_BLOCKS_PER_TICK
			val step = 1.0 / CoarseMoveRates.STEP_UP_BLOCKS_PER_TICK

			fun ballistic(jumping: Boolean, rise: Int): Double? {
				val height = minOf(rise.toDouble(), LaunchMode.MAX_JUMP_RISE)
				return LaunchMode.entries
					.filter { it.jumps == jumping && it.supports(height) }
					.mapNotNull { mode -> profile.fly(mode, profile.cruiseSpeed(mode.sprint), height)?.airTicks }
					.minOrNull()?.let { (it + 1).toDouble() }
			}

			fun jump(horizontalDistance: Double, verticalOffset: Int): Double =
				max(
					ballistic(jumping = true, rise = verticalOffset) ?: UNREACHABLE_BALLISTIC_TICKS,
					horizontalDistance / CoarseMoveRates.SPRINT_JUMP_BLOCKS_PER_TICK,
				)

			return CoarseMoveCosts(
				cardinalWalk = walk + transitionOverheadTicks,
				diagonalWalk = sqrt(2.0) * walk + transitionOverheadTicks,
				cornerWalk = 2.0 * (walk + transitionOverheadTicks),
				stepUp = step + transitionOverheadTicks,
				walkOff = { depth -> max(walk, CoarseMoveRates.fallTicks(depth)) + 1.0 + transitionOverheadTicks },
				jumpCandidate = { distance, vo -> jump(distance, vo) + JUMP_SETTLE_TICKS + transitionOverheadTicks },
				swim = 1.0 / CoarseMoveRates.SWIM_BLOCKS_PER_TICK + transitionOverheadTicks,
				diagonalSwim = sqrt(2.0) / CoarseMoveRates.SWIM_BLOCKS_PER_TICK + transitionOverheadTicks,
				waterExit = { rise -> CoarseMoveRates.waterExitTicks(rise) + transitionOverheadTicks },
				drop = { distance, depth ->

					max(
						ballistic(jumping = false, rise = -depth) ?: UNREACHABLE_BALLISTIC_TICKS,
						distance / CoarseMoveRates.SPRINT_BLOCKS_PER_TICK,
					) + DROP_SETTLE_TICKS + transitionOverheadTicks
				},
			)
		}
	}
}
