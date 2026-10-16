package com.lambda.pathing.actions.families

import com.lambda.pathing.actions.CellPredicate
import com.lambda.pathing.actions.control.BounceProgram
import com.lambda.pathing.actions.CompletionContext
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.actions.Movement
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.StanceRules
import com.lambda.pathing.actions.TemplateSpec
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.BounceSolver
import com.lambda.pathing.physics.Vanilla
import kotlin.math.abs
import kotlin.math.hypot

object SlimeBounceMovement : Movement {
	override val id = MovementId.BOUNCE

	override fun templates(context: MovementContext): List<TemplateSpec> = buildList {
		val options = context.options
		if (!options.allowSlimeBounces) return@buildList

		for (drop in MIN_DROP..minOf(options.maxBounceDrop, TEMPLATE_MAX_DROP)) {
			for (rise in -(drop - 1)..MAX_RISE) {
				val ledge = if (BounceSolver.reach(drop, rise) != null) 0.0 else LOWEST_LEDGE_DEPTH
				val window = BounceSolver.reach(drop, rise, ledgeDepth = ledge) ?: continue

				for ((dx, dz) in StanceRules.CARDINALS + StanceRules.DIAGONALS) {
					val perStep = hypot(dx.toDouble(), dz.toDouble())
					for (span in 1..MAX_SPAN) {
						val distance = span * perStep
						if (distance !in window) continue
						spec(span * dx, span * dz, drop, rise, context, ledgeDepth = ledge)?.let(::add)
					}
				}
			}
		}
		for (drop in MIN_DROP..minOf(options.maxBounceDrop, TEMPLATE_MAX_DROP)) {
			for (rise in -(drop - 1)..MAX_RISE) {
				val window = BounceSolver.reach(drop, rise, DOUBLE_BOUNCES) ?: continue
				for ((dx, dz) in StanceRules.CARDINALS + StanceRules.DIAGONALS) {
					val perStep = hypot(dx.toDouble(), dz.toDouble())
					for (span in 1..MAX_DOUBLE_SPAN) {
						if (span * perStep in window) spec(span * dx, span * dz, drop, rise, context, DOUBLE_BOUNCES)?.let(::add)
					}
				}
			}
		}
		if (!options.allowOffAxisJumps) return@buildList
		for (drop in MIN_DROP..minOf(options.maxBounceDrop, TEMPLATE_MAX_DROP)) {
			for (rise in -(drop - 1)..MAX_RISE) {
				val ledge = if (BounceSolver.reach(drop, rise) != null) 0.0 else LOWEST_LEDGE_DEPTH
				val window = BounceSolver.reach(drop, rise, ledgeDepth = ledge) ?: continue
				for (dx in -MAX_SPAN..MAX_SPAN) for (dz in -MAX_SPAN..MAX_SPAN) {
					if (dx == 0 || dz == 0 || abs(dx) == abs(dz)) continue
					if (hypot(dx.toDouble(), dz.toDouble()) !in window) continue
					spec(dx, dz, drop, rise, context, ledgeDepth = ledge)?.let(::add)
				}
			}
		}
	}

	private fun spec(
		dx: Int,
		dz: Int,
		drop: Int,
		rise: Int,
		context: MovementContext,
		bounces: Int = 1,
		ledgeDepth: Double = 0.0,
	): TemplateSpec? = TemplateSpec(
		dx = dx,
		dy = rise,
		dz = dz,
		movement = id,
		cost = context.costs.bounceCost(drop, rise, hypot(dx.toDouble(), dz.toDouble()), bounces, ledgeDepth) ?: return null,
		conditions = StanceRules.stanceConditions(dx, rise, dz),
		departure = CellPredicate.LAUNCHABLE,
		arc = MotionTemplate.ArcSpec(bounceDrop = drop, bounces = bounces),
	)

	override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
		val solution = context.edge.bounce ?: return emptyList()
		if (!context.constraints.sprintModes.contains(solution.sprint)) return emptyList()
		val standing = TrajectoryDecision.Bounce(context.edge.to, solution)
		if (!solution.pressesWall) return listOf(standing)
		val running = solution.copy(speed = BallisticProfile.VANILLA.momentumSpeed(solution.sprint), speedSlack = 0.0)
		return listOf(TrajectoryDecision.Bounce(context.edge.to, running), standing)
	}

	override fun descentAllowance(decision: TrajectoryDecision): Double =
		(decision as TrajectoryDecision.Bounce).solution.contactDepth

	override fun transitionFrames(decision: TrajectoryDecision): Int =
		(decision as TrajectoryDecision.Bounce).solution.airTicks + TRANSITION_MARGIN_FRAMES

	override fun program(context: ProgramContext): ControlProgram =
		BounceProgram(context.decision as TrajectoryDecision.Bounce, context)

	override fun completed(context: CompletionContext): Boolean {
		if (!context.airborne || !context.observed.onGround) return false
		if (context.observed.velocity.y > Vanilla.SLIME_STEP_MAX_VERTICAL_SPEED) return false
		val step = context.decision.step

		return context.stance.y == step.y &&
				abs(context.stance.x - step.x) <= 1 &&
				abs(context.stance.z - step.z) <= 1
	}

	private const val TRANSITION_MARGIN_FRAMES = 20

	private const val MAX_SPAN = 12

	private const val MAX_DOUBLE_SPAN = 24

	internal const val DOUBLE_BOUNCES = 2

	internal const val MIN_DROP = 2

	const val TEMPLATE_MAX_DROP = 12

	private const val MAX_RISE = 0

	private const val LOWEST_LEDGE_DEPTH = 15.0 / 16.0
}
