package com.lambda.pathing.actions.families.jump

import com.lambda.pathing.actions.CellPredicate
import com.lambda.pathing.actions.CoarseMoveRates
import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.actions.StanceRules
import com.lambda.pathing.actions.TemplateSpec
import com.lambda.pathing.actions.families.FallMovement
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.world.CoarseVoxel
import kotlin.math.abs

internal object JumpTemplates {

	val MODES = LaunchMode.entries.filter { it.jumps }

	fun templates(context: MovementContext): List<TemplateSpec> = buildList {
		val options = context.options
		if (!options.allowJumpCandidates) return@buildList

		val tuned = minOf(options.maxJumpSpan, SimpleMoveOptions.TUNED_JUMP_SPAN)
		val reach = if (options.allowDeepDropJumps) maxOf(options.maxJumpSpan, WIDE_DROP_SPAN) else options.maxJumpSpan
		val deepest = if (options.allowDeepDropJumps) maxOf(options.maxJumpDrop, FallMovement.MIN_DROP - 1) else options.maxJumpDrop
		val stretching = options.allowDeepDropJumps && options.maxJumpSpan > SimpleMoveOptions.TUNED_JUMP_SPAN
		for (rise in -deepest..LaunchMode.MAX_JUMP_STEP) {
			for (dx in -reach..reach) {
				for (dz in -reach..reach) {
					if (-rise > options.maxJumpDrop && maxOf(abs(dx), abs(dz)) <= options.maxDropSpan) continue
					val offered = maxOf(abs(dx), abs(dz)) <= reach && offered(dx, dz, rise, options, tuned)
					val stretched = !offered && stretching && maxOf(abs(dx), abs(dz)) <= options.maxJumpSpan && stretched(dx, dz, rise, options)
					if (!offered && !stretched) continue
					val wide = stretched || maxOf(abs(dx), abs(dz)) > tuned
					val cost = (context.costs.jumpCandidateCost(hypot(dx, dz), rise) ?: continue) + (if (wide) WIDE_DROP_RISK_TICKS else 0.0) + options.jumpPenaltyTicks
					add(if (stretched) stretchedSpec(dx, dz, rise, cost) else spec(dx, dz, rise, cost, momentumOnly = wide))
				}
			}
		}
	}

	private fun stretched(dx: Int, dz: Int, rise: Int, options: SimpleMoveOptions): Boolean {
		if (!allowedHeading(dx, dz, options)) return false
		if (hypot(dx, dz) < CoarseMoveRates.MIN_JUMP_DISTANCE) return false
		return LaunchSolver.solve(Stance(0, 0, 0), Stance(dx, 0, dz), MODES, rise = rise - 1.0).isNotEmpty()
	}

	private fun allowedHeading(dx: Int, dz: Int, options: SimpleMoveOptions): Boolean {
		if (dx == 0 && dz == 0) return false
		if (dx != 0 && dz != 0 && !options.allowDiagonal) return false
		return abs(dx) == abs(dz) || dx == 0 || dz == 0 || options.allowOffAxisJumps
	}

	private fun offered(dx: Int, dz: Int, rise: Int, options: SimpleMoveOptions, tuned: Int): Boolean {
		val distance = hypot(dx, dz)
		val step = rise == LaunchMode.MAX_JUMP_STEP
		if (!allowedHeading(dx, dz, options)) return false
		if (distance < CoarseMoveRates.MIN_JUMP_DISTANCE && !step && !diagonalStep(dx, dz, rise)) return false

		if (maxOf(abs(dx), abs(dz)) > tuned) return rise <= WIDE_DROP_STEP && options.allowDeepDropJumps && wideDrop(dx, dz)
		val ceiling = when {
			step -> RISING_STANDING_AIR_GAP_BLOCKS
			rise < 0 -> if (options.allowDeepDropJumps) FULL_DROP_AIR_GAP_BLOCKS else STANDING_AIR_GAP_BLOCKS
			else -> LEVEL_MOMENTUM_AIR_GAP_BLOCKS
		}
		return airGap(dx, dz) <= ceiling + REACH_EPSILON
	}

	private fun diagonalStep(dx: Int, dz: Int, rise: Int) = rise < 0 && abs(dx) == 1 && abs(dz) == 1

	private fun wideDrop(dx: Int, dz: Int) = maxOf(abs(dx), abs(dz)) == WIDE_DROP_SPAN && minOf(abs(dx), abs(dz)) <= 1

	private fun airGap(dx: Int, dz: Int): Double = kotlin.math.hypot(
		(abs(dx) - 1).coerceAtLeast(0).toDouble(),
		(abs(dz) - 1).coerceAtLeast(0).toDouble(),
	)

	private fun hypot(dx: Int, dz: Int): Double = kotlin.math.hypot(dx.toDouble(), dz.toDouble())

	private fun spec(dx: Int, dz: Int, rise: Int, cost: Double, momentumOnly: Boolean): TemplateSpec {
		val gap = airGap(dx, dz)
		return TemplateSpec(
			dx = dx, dy = rise, dz = dz,
			movement = MovementId.JUMP,
			cost = cost,
			conditions = StanceRules.stanceConditions(dx, rise, dz, CoarseVoxel.sides(-dx, -dz)) + StanceRules.leaving(CoarseVoxel.sides(dx, dz), 0) +
				StanceRules.targetCorner(dx, maxOf(0, rise), dz, hop = true),
			departure = CellPredicate.LAUNCHABLE,
			arc = MotionTemplate.ArcSpec(
				MODES,
				riseAdmission = { realRise ->
					when {
						realRise > LaunchMode.MAX_JUMP_RISE -> false
						gap <= RISING_STANDING_AIR_GAP_BLOCKS + REACH_EPSILON -> true
						gap <= RISING_MOMENTUM_AIR_GAP_BLOCKS + REACH_EPSILON && realRise <= MOMENTUM_RISE -> true
						realRise > NEAR_FLAT_RISE -> false
						gap <= STANDING_AIR_GAP_BLOCKS + REACH_EPSILON -> true
						gap <= LEVEL_MOMENTUM_AIR_GAP_BLOCKS + REACH_EPSILON && realRise <= 0.0 -> true
						realRise > HALF_DROP_RISE -> false
						gap <= HALF_DROP_AIR_GAP_BLOCKS + REACH_EPSILON -> true
						realRise > FULL_DROP_RISE -> false
						gap <= FULL_DROP_AIR_GAP_BLOCKS + REACH_EPSILON -> true
						else -> realRise <= WIDE_DROP_RISE
					}
				},
				momentumOnly = momentumOnly,
				runUpNeeded = { realRise -> needsMomentum(gap, realRise) },
			),
		)
	}

	private fun stretchedSpec(dx: Int, dz: Int, rise: Int, cost: Double): TemplateSpec = TemplateSpec(
		dx = dx, dy = rise, dz = dz,
		movement = MovementId.JUMP,
		cost = cost,
		conditions = StanceRules.stanceConditions(dx, rise, dz, CoarseVoxel.sides(-dx, -dz)) + StanceRules.leaving(CoarseVoxel.sides(dx, dz), 0) +
			StanceRules.targetCorner(dx, maxOf(0, rise), dz, hop = true),
		departure = CellPredicate.LAUNCHABLE,
		arc = MotionTemplate.ArcSpec(MODES, riseAdmission = { realRise -> realRise <= LaunchMode.MAX_JUMP_RISE }, momentumOnly = true, exactOnly = true),
	)

	private fun needsMomentum(gap: Double, realRise: Double): Boolean =
		realRise > NEAR_FLAT_RISE && gap > RISING_STANDING_AIR_GAP_BLOCKS + REACH_EPSILON ||
				realRise > HALF_DROP_RISE && gap > STANDING_AIR_GAP_BLOCKS + REACH_EPSILON

	private const val REACH_EPSILON = 1e-9

	private val STANDING_AIR_GAP_BLOCKS = kotlin.math.hypot(3.0, 1.0)

	private val RISING_STANDING_AIR_GAP_BLOCKS = kotlin.math.hypot(2.0, 2.0)

	private const val RISING_MOMENTUM_AIR_GAP_BLOCKS = 3.0

	private const val MOMENTUM_RISE = 1.0

	private const val LEVEL_MOMENTUM_AIR_GAP_BLOCKS = 4.0

	private const val HALF_DROP_AIR_GAP_BLOCKS = 4.0

	private val FULL_DROP_AIR_GAP_BLOCKS = kotlin.math.hypot(3.0, 3.0)

	private const val WIDE_DROP_SPAN = 6

	private const val WIDE_DROP_STEP = -2

	private const val WIDE_DROP_RISE = -1.75

	private const val WIDE_DROP_RISK_TICKS = 24.0

	private const val HALF_DROP_RISE = -0.25

	private const val FULL_DROP_RISE = -0.75

	private const val NEAR_FLAT_RISE = 0.75
}
