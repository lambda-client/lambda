package com.lambda.pathing.actions.families

import com.lambda.pathing.actions.CellCondition
import com.lambda.pathing.actions.CellPredicate
import com.lambda.pathing.actions.CoarseMoveRates
import com.lambda.pathing.actions.control.ClimbProgram
import com.lambda.pathing.actions.CompletionContext
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.Movement
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.StanceRules
import com.lambda.pathing.actions.TemplateSpec
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.bearingBetween
import com.lambda.pathing.core.center
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.Medium

object ClimbMovement : Movement {
	override val id = MovementId.CLIMB

	override fun templates(context: MovementContext): List<TemplateSpec> {
		if (!context.options.allowClimbing) return emptyList()
		return buildList {
			add(
				spec(
					0, 1, 0,
					CellCondition(0, 0, 0, CellPredicate.CLIMBABLE),
					CellCondition(0, 1, 0, CellPredicate.CLIMBABLE),
					CellCondition(0, 2, 0, CellPredicate.CENTER_HEAD),
				)
			)
			add(spec(0, -1, 0, CellCondition(0, 0, 0, CellPredicate.CLIMBABLE), CellCondition(0, -1, 0, CellPredicate.CLIMBABLE)))
			for ((dx, dz) in StanceRules.CARDINALS) {
				add(spec(dx, -1, dz, CellCondition(dx, -1, dz, CellPredicate.CLIMBABLE), CellCondition(dx, 0, dz, CellPredicate.CENTER_HEAD)))
			}
			for ((dx, dz) in StanceRules.CARDINALS) {
				add(spec(dx, 0, dz, CellCondition(dx, 0, dz, CellPredicate.CLIMBABLE), CellCondition(dx, 1, dz, CellPredicate.CENTER_HEAD)))
				add(spec(dx, 0, dz, CellCondition(0, 0, 0, CellPredicate.CLIMBABLE), *StanceRules.stanceConditions(dx, 0, dz).toTypedArray()))
			}
		}
	}

	private fun spec(dx: Int, dy: Int, dz: Int, vararg conditions: CellCondition) = TemplateSpec(
		dx = dx, dy = dy, dz = dz,
		movement = id,
		cost = CoarseMoveRates.CLIMB_TICKS_PER_BLOCK,
		conditions = conditions.toList(),
	)

	override fun occupies(view: CoarseVoxelView, stance: Stance): Boolean =
		view.medium(stance.x, stance.y, stance.z) == Medium.CLIMBABLE

	override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
		val edge = context.edge
		val vertical = edge.from.x == edge.to.x && edge.from.z == edge.to.z

		if (!vertical) {
			val enteringOverLip = edge.to.y < edge.from.y
			val yaw = bearingBetween(edge.from.center(), edge.to.center())
			return listOf(
				TrajectoryDecision.Climb(
					step = edge.to,
					holdForward = true,
					holdWhileClimbing = !enteringOverLip,
					climbWithJump = false,
					yaw = yaw,
				)
			)
		}

		val hold = supportBearing(context.view, edge.from, edge.to)
		val ascending = edge.to.y > edge.from.y
		return listOf(
			TrajectoryDecision.Climb(
				step = edge.to,
				holdForward = ascending && hold != null,
				holdWhileClimbing = ascending && hold != null,
				climbWithJump = ascending,
				yaw = hold,
			)
		)
	}

	private fun supportBearing(
		view: CoarseVoxelView,
		from: Stance,
		to: Stance,
	): Double? {
		val support = StanceRules.CARDINALS.firstOrNull { (dx, dz) ->
			view.medium(from.x + dx, from.y, from.z + dz) == Medium.SOLID &&
					view.medium(to.x + dx, to.y, to.z + dz) == Medium.SOLID
		} ?: return null
		val (dx, dz) = support
		return bearingBetween(from.center(), from.offset(dx, 0, dz).center())
	}

	override fun program(context: ProgramContext): ControlProgram =
		ClimbProgram(context.decision as TrajectoryDecision.Climb, context)

	override fun completed(context: CompletionContext): Boolean = context.stance == context.decision.step

	override val climbs: Boolean get() = true
}
