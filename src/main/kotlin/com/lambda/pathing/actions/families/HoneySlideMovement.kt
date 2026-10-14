package com.lambda.pathing.actions.families

import com.lambda.pathing.actions.CellCondition
import com.lambda.pathing.actions.CellPredicate
import com.lambda.pathing.actions.CompletionContext
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.Movement
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.StanceRules
import com.lambda.pathing.actions.TemplateSpec
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.actions.control.HoneySlideProgram
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.world.CoarseVoxelView
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign

object HoneySlideMovement : Movement {
	override val id = MovementId.HONEY_SLIDE

	override fun templates(context: MovementContext): List<TemplateSpec> = buildList {
		if (!context.options.allowJumpCandidates) return@buildList
		for ((ux, uz) in StanceRules.CARDINALS) {
			for (span in MIN_SPAN..MAX_SPAN) for (rise in -MAX_DROP..0) {
				val corridor = (1 until span).flatMap { k -> (rise..CORRIDOR_TOP).map { h -> CellCondition(ux * k, h, uz * k, CellPredicate.CENTER_HEAD) } }
				add(
					TemplateSpec(
						dx = ux * span,
						dy = rise,
						dz = uz * span,
						movement = id,
						cost = span / RUN_BLOCKS_PER_TICK + SLIDE_SETTLE_TICKS,
						conditions = listOf(CellCondition(0, 0, 0, HoneyWall(ux, uz, span, rise))) +
								StanceRules.stanceConditions(ux * span, rise, uz * span) + corridor,
						departure = CellPredicate.LAUNCHABLE,
					)
				)
			}
		}
	}

	override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
		val edge = context.edge
		val ux = (edge.to.x - edge.from.x).sign
		val uz = (edge.to.z - edge.from.z).sign
		val span = abs(edge.to.x - edge.from.x) + abs(edge.to.z - edge.from.z)
		val yaw = Math.toDegrees(atan2(-ux.toDouble(), uz.toDouble()))
		return buildList {
			for (strafe in listOf(1.0, -1.0)) {
				val (sx, sz) = leftOf(ux, uz).let { (lx, lz) -> (lx * strafe.toInt()) to (lz * strafe.toInt()) }
				if (wallCells(context.view, edge.from.x, edge.from.y, edge.from.z, ux, uz, sx, sz, span, edge.to.y - edge.from.y) < MIN_WALL_CELLS) continue
				for (jumpAlong in JUMP_ALONG) for (strafeDelay in STRAFE_DELAYS) {
					add(TrajectoryDecision.HoneySlide(edge.to, yaw, jumpAlong, strafe, strafeDelay))
				}
			}
		}
	}

	override fun program(context: ProgramContext): ControlProgram =
		HoneySlideProgram(context.decision as TrajectoryDecision.HoneySlide, context)

	override fun completed(context: CompletionContext): Boolean = context.observed.onGround && context.stance == context.decision.step

	override val inMedium: Boolean get() = true

	override fun transitionFrames(decision: TrajectoryDecision): Int = SLIDE_FRAMES

	private fun leftOf(ux: Int, uz: Int): Pair<Int, Int> = uz to -ux

	private fun wallCells(view: CoarseVoxelView, x: Int, y: Int, z: Int, ux: Int, uz: Int, sx: Int, sz: Int, span: Int, rise: Int): Int {
		var cells = 0
		for (k in 1 until span) for (h in rise..WALL_TOP) {
			if (view.honey(x + ux * k + sx, y + h, z + uz * k + sz)) cells++
		}
		return cells
	}

	private class HoneyWall(private val ux: Int, private val uz: Int, private val span: Int, private val rise: Int) : CellPredicate {
		private val sides = listOf(leftOf(ux, uz), leftOf(ux, uz).let { (lx, lz) -> -lx to -lz })

		override fun matches(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean {
			val far = span - 1
			if (!view.mayContainHoney(x + minOf(0, ux * far) - 1, y + rise, z + minOf(0, uz * far) - 1, x + maxOf(0, ux * far) + 1, y + WALL_TOP, z + maxOf(0, uz * far) + 1)) return false
			return sides.any { (sx, sz) -> wallCells(view, x, y, z, ux, uz, sx, sz, span, rise) >= MIN_WALL_CELLS }
		}

		override val neighborReads: List<VoxelPos> = sides.flatMap { (sx, sz) ->
			(1 until span).flatMap { k -> (rise..WALL_TOP).map { h -> VoxelPos(ux * k + sx, h, uz * k + sz) } }
		}
	}

	private const val MIN_SPAN = 3

	private const val MAX_SPAN = 8

	private const val MAX_DROP = 4

	private const val CORRIDOR_TOP = 2

	private const val WALL_TOP = 1

	private const val MIN_WALL_CELLS = 2

	private const val RUN_BLOCKS_PER_TICK = 0.3

	private const val SLIDE_SETTLE_TICKS = 4.0

	private const val SLIDE_FRAMES = 80

	private val JUMP_ALONG = listOf(0.3, 0.55, 0.8)

	private val STRAFE_DELAYS = listOf(0, 3, 6)
}
