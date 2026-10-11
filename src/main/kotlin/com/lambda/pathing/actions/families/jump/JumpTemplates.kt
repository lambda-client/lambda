package com.lambda.pathing.actions.families.jump

import com.lambda.pathing.actions.CellPredicate
import com.lambda.pathing.actions.CoarseMoveRates
import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.actions.StanceRules
import com.lambda.pathing.actions.TemplateSpec
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.world.CoarseVoxel
import kotlin.math.abs

internal object JumpTemplates {

	val MODES = LaunchMode.entries.filter { it.jumps }

	fun templates(context: MovementContext): List<TemplateSpec> = buildList {
		val options = context.options
		if (!options.allowJumpCandidates) return@buildList

		val reach = if (options.allowDeepDropJumps) maxOf(options.maxJumpSpan, WIDE_DROP_SPAN) else options.maxJumpSpan
		for (rise in -options.maxJumpDrop..LaunchMode.MAX_JUMP_STEP) {
			for (dx in -reach..reach) {
				for (dz in -reach..reach) {
					if (!offered(dx, dz, rise, options)) continue
					val wide = maxOf(abs(dx), abs(dz)) > options.maxJumpSpan
					val cost = context.costs.jumpCandidateCost(hypot(dx, dz), rise) + if (wide) WIDE_DROP_RISK_TICKS else 0.0
					add(spec(dx, dz, rise, cost, momentumOnly = wide))
				}
			}
		}
	}

	private fun offered(dx: Int, dz: Int, rise: Int, options: SimpleMoveOptions): Boolean {
		val distance = hypot(dx, dz)
		val step = rise == LaunchMode.MAX_JUMP_STEP
		if (dx == 0 && dz == 0) return false
		if (distance < CoarseMoveRates.MIN_JUMP_DISTANCE && !step && !diagonalStep(dx, dz, rise)) return false
		if (dx != 0 && dz != 0 && !options.allowDiagonal) return false
		if (abs(dx) != abs(dz) && dx != 0 && dz != 0 && !options.allowOffAxisJumps) return false

		if (maxOf(abs(dx), abs(dz)) > options.maxJumpSpan) return rise <= WIDE_DROP_STEP && options.allowDeepDropJumps && wideDrop(dx, dz)
		val ceiling = when {
			step -> STEP_JUMP_AIR_GAP_BLOCKS
			rise < 0 && options.allowDeepDropJumps -> FULL_DROP_AIR_GAP_BLOCKS
			else -> STANDING_AIR_GAP_BLOCKS
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
			conditions = StanceRules.stanceConditions(dx, rise, dz, CoarseVoxel.sides(-dx, -dz)) + StanceRules.leaving(CoarseVoxel.sides(dx, dz), 0),
			departure = CellPredicate.LAUNCHABLE,
			arc = MotionTemplate.ArcSpec(
				MODES,
				riseAdmission = { realRise ->
					when {
						realRise > LaunchMode.MAX_JUMP_RISE -> false
						gap <= RISING_STANDING_AIR_GAP_BLOCKS + REACH_EPSILON -> true
						realRise > NEAR_FLAT_RISE -> false
						gap <= STANDING_AIR_GAP_BLOCKS + REACH_EPSILON -> true
						realRise > HALF_DROP_RISE -> false
						gap <= HALF_DROP_AIR_GAP_BLOCKS + REACH_EPSILON -> true
						realRise > FULL_DROP_RISE -> false
						gap <= FULL_DROP_AIR_GAP_BLOCKS + REACH_EPSILON -> true
						else -> realRise <= WIDE_DROP_RISE
					}
				},
				momentumOnly = momentumOnly,
			),
		)
	}

	private const val REACH_EPSILON = 1e-9

	private val STANDING_AIR_GAP_BLOCKS = kotlin.math.hypot(3.0, 1.0)

	private val RISING_STANDING_AIR_GAP_BLOCKS = kotlin.math.hypot(2.0, 2.0)

	private const val STEP_JUMP_AIR_GAP_BLOCKS = 1.0

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
