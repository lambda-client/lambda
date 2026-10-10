package com.lambda.pathing.actions.families

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.CompletionContext
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.DecisionPrice
import com.lambda.pathing.actions.control.DropProgram
import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.actions.Movement
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.StanceRules
import com.lambda.pathing.actions.TemplateSpec
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.actions.landingRisk
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.launch.LaunchSolver

object DropMovement : Movement {
	override val id = MovementId.DROP

	override fun templates(context: MovementContext): List<TemplateSpec> = buildList {
		val options = context.options
		for ((dx, dz) in StanceRules.CARDINALS) {
			for (depth in 2..options.maxWalkOffDepth) {
				add(spec(dx, dz, span = 1, depth = depth, cost = context.costs.dropCost(1, depth)))
			}
			for (span in 2..options.maxDropSpan) {
				for (depth in 1..options.maxWalkOffDepth) {
					add(spec(dx, dz, span, depth, context.costs.dropCost(span, depth)))
				}
			}
		}
	}

	private fun spec(dx: Int, dz: Int, span: Int, depth: Int, cost: Double) = TemplateSpec(
		dx = span * dx, dy = -depth, dz = span * dz,
		movement = id,
		cost = cost,
		conditions = StanceRules.stanceConditions(span * dx, -depth, span * dz),
		arc = MotionTemplate.ArcSpec(MODES),
	)

	override fun offersFor(edge: CoarseEdge): Boolean = edge.to.y < edge.from.y && edge.movement !in MovementId.AQUATIC

	override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
		val ideal = context.edge.launch
		val asIs = LaunchSolver.solve(
			context.edge.from, context.edge.to,
			modes = MODES,
			maxEntrySpeed = { context.body.speed },
			preferredEntrySpeed = { context.body.speed },
		).firstOrNull()
		return listOfNotNull(ideal, asIs)
			.filter { context.constraints.sprintModes.contains(it.sprint) }
			.distinctBy { it.mode to it.holdForward }
			.sortedWith(LaunchSolution.BEST_FIRST)
			.map { TrajectoryDecision.Drop(context.edge.to, it) }
	}

	override fun price(decision: TrajectoryDecision, context: DecisionContext): DecisionPrice {
		val drop = decision as TrajectoryDecision.Drop
		return DecisionPrice(difficulty = context.landingRisk(drop.solution))
	}

	override fun program(context: ProgramContext): ControlProgram =
		DropProgram(context.decision as TrajectoryDecision.Drop, context)

	override fun completed(context: CompletionContext): Boolean = context.airborne

	private val MODES = LaunchMode.entries.filter { it.drops }
}
