package com.lambda.pathing.actions.families

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
import com.lambda.pathing.core.center
import com.lambda.pathing.launch.BounceSolver
import kotlin.math.abs
import kotlin.math.hypot

object SlimeBounceMovement : Movement {
	override val id = MovementId.BOUNCE

	override fun templates(context: MovementContext): List<TemplateSpec> = buildList {
		val options = context.options
		if (!options.allowSlimeBounces) return@buildList

		for (drop in MIN_DROP..options.maxBounceDrop) {
			for (rise in -(drop - 1)..MAX_RISE) {
				val window = BounceSolver.reach(drop, rise) ?: continue

				for ((dx, dz) in StanceRules.CARDINALS + StanceRules.DIAGONALS) {
					val perStep = hypot(dx.toDouble(), dz.toDouble())
					for (span in 1..MAX_SPAN) {
						val distance = span * perStep
						if (distance !in window) continue
						add(spec(dx, dz, span, drop, rise, context))
					}
				}
			}
		}
	}

	private fun spec(
		dx: Int,
		dz: Int,
		span: Int,
		drop: Int,
		rise: Int,
		context: MovementContext,
	) = TemplateSpec(
		dx = span * dx,
		dy = rise,
		dz = span * dz,
		movement = id,
		cost = context.costs.bounceCost(drop, rise),
		conditions = StanceRules.stanceConditions(span * dx, rise, span * dz),
		arc = MotionTemplate.ArcSpec(bounceDrop = drop),
	)

	override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
		val solution = context.edge.bounce ?: return emptyList()
		if (!context.constraints.sprintModes.contains(solution.sprint)) return emptyList()
		return listOf(TrajectoryDecision.Bounce(context.edge.to, solution))
	}

	override fun descentAllowance(decision: TrajectoryDecision): Double =
		(decision as TrajectoryDecision.Bounce).solution.contactDepth

	override fun transitionFrames(decision: TrajectoryDecision): Int =
		(decision as TrajectoryDecision.Bounce).solution.airTicks + TRANSITION_MARGIN_FRAMES

	override fun program(context: ProgramContext): ControlProgram {
		val decision = context.decision as TrajectoryDecision.Bounce
		val from = context.body.stance.center()
		return BounceProgram(
			takeoff = from,
			aim = decision.step.center(),
			solution = decision.solution,
			maxYawChange = context.constraints.maxYawDegreesPerFrame,
		)
	}

	override fun completed(context: CompletionContext): Boolean {
		if (!context.airborne || !context.observed.onGround) return false
		if (context.observed.velocity.y > SETTLED_VERTICAL_SPEED) return false
		val step = context.decision.step

		return context.stance.y == step.y &&
				abs(context.stance.x - step.x) <= 1 &&
				abs(context.stance.z - step.z) <= 1
	}

	private const val TRANSITION_MARGIN_FRAMES = 20

	private const val SETTLED_VERTICAL_SPEED = 0.1

	private const val MAX_SPAN = 12

	private const val MIN_DROP = 2

	private const val MAX_RISE = 0
}
