package com.lambda.pathing.actions.families.jump

import com.lambda.pathing.actions.CoarseMoveRates
import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.actions.StanceRules
import com.lambda.pathing.actions.TemplateSpec
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.launch.LaunchMode
import kotlin.math.abs

internal object JumpTemplates {

	val MODES = LaunchMode.entries.filter { it.jumps }

	fun templates(context: MovementContext, movement: MovementId): List<TemplateSpec> = buildList {
		val options = context.options
		if (!options.allowJumpCandidates) return@buildList

		val reach = options.maxJumpSpan
		for (rise in -options.maxJumpDrop..LaunchMode.MAX_JUMP_RISE) {
			for (dx in -reach..reach) {
				for (dz in -reach..reach) {
					if (!offered(dx, dz, rise, options)) continue
					add(spec(dx, dz, rise, context.costs.jumpCandidateCost(hypot(dx, dz), rise), movement))
				}
			}
		}
	}

	private fun offered(dx: Int, dz: Int, rise: Int, options: SimpleMoveOptions): Boolean {
		val distance = hypot(dx, dz)
		if (distance < CoarseMoveRates.MIN_JUMP_DISTANCE) return false
		if (dx != 0 && dz != 0 && !options.allowDiagonal) return false
		if (abs(dx) != abs(dz) && dx != 0 && dz != 0 && !options.allowOffAxisJumps) return false
		if (maxOf(abs(dx), abs(dz)) > options.maxJumpSpan) return false

		val ceiling = if (rise < 0 && options.allowDeepDropJumps) FULL_DROP_AIR_GAP_BLOCKS
		else STANDING_AIR_GAP_BLOCKS
		return airGap(dx, dz) <= ceiling + REACH_EPSILON
	}

	private fun airGap(dx: Int, dz: Int): Double = kotlin.math.hypot(
		(abs(dx) - 1).coerceAtLeast(0).toDouble(),
		(abs(dz) - 1).coerceAtLeast(0).toDouble(),
	)

	private fun hypot(dx: Int, dz: Int): Double = kotlin.math.hypot(dx.toDouble(), dz.toDouble())

	private fun spec(dx: Int, dz: Int, rise: Int, cost: Double, movement: MovementId): TemplateSpec {
		val gap = airGap(dx, dz)
		return TemplateSpec(
			dx = dx, dy = rise, dz = dz,
			movement = movement,
			cost = cost,
			conditions = StanceRules.stanceConditions(dx, rise, dz),
			arc = MotionTemplate.ArcSpec(
				dx, dz, rise, MODES,

				riseAdmission = { realRise ->
					when {
						gap <= RISING_STANDING_AIR_GAP_BLOCKS + REACH_EPSILON -> true
						realRise > NEAR_FLAT_RISE -> false
						gap <= STANDING_AIR_GAP_BLOCKS + REACH_EPSILON -> true
						realRise > HALF_DROP_RISE -> false
						gap <= HALF_DROP_AIR_GAP_BLOCKS + REACH_EPSILON -> true
						else -> realRise <= FULL_DROP_RISE
					}
				},
			),
		)
	}

	private const val REACH_EPSILON = 1e-9

	private val STANDING_AIR_GAP_BLOCKS = kotlin.math.hypot(3.0, 1.0)

	private val RISING_STANDING_AIR_GAP_BLOCKS = kotlin.math.hypot(2.0, 2.0)

	private const val HALF_DROP_AIR_GAP_BLOCKS = 4.0

	private val FULL_DROP_AIR_GAP_BLOCKS = kotlin.math.hypot(3.0, 3.0)

	private const val HALF_DROP_RISE = -0.25

	private const val FULL_DROP_RISE = -0.75

	private const val NEAR_FLAT_RISE = 0.75
}
