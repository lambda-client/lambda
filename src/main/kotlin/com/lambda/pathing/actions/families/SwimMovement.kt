package com.lambda.pathing.actions.families

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.CellCondition
import com.lambda.pathing.actions.CellPredicate
import com.lambda.pathing.actions.CompletionContext
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.Movement
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.ProposalContext
import com.lambda.pathing.actions.StanceRules.CARDINALS
import com.lambda.pathing.actions.StanceRules.DIAGONALS
import com.lambda.pathing.actions.StanceRules.stanceConditions
import com.lambda.pathing.actions.TemplateSpec
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.actions.control.SwimProgram
import com.lambda.pathing.actions.control.yawTowards
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.center
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.Medium
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

object SwimMovement : Movement {
	override val id = MovementId.SWIM

	override fun templates(context: MovementContext): List<TemplateSpec> {
		if (!context.options.allowSwimming) return emptyList()
		val costs = context.costs
		return buildList {
			for ((dx, dz) in CARDINALS) {
				add(TemplateSpec(dx, 0, dz, id, costs.swim, surface(dx, dz), departure = CellPredicate.WATER))
				add(TemplateSpec(dx, 0, dz, MovementId.ENTER_WATER, costs.cardinalWalk, surface(dx, dz)))
				add(TemplateSpec(dx, 0, dz, MovementId.ENTER_WATER, costs.cardinalWalk, submergedEntry(dx, dz)))
				for (depth in 1..MAX_ENTRY_DEPTH) {
					val fall = (1 downTo 1 - depth).map { CellCondition(dx, it, dz, CellPredicate.CENTER_SLICE) }
					val cost = if (depth == 1) costs.walkOffCost(depth) else costs.dropCost(1.0, depth)
					add(TemplateSpec(dx, -depth, dz, MovementId.ENTER_WATER, cost, listOf(CellCondition(dx, -depth, dz, CellPredicate.WATER)) + fall))
				}
				for (rise in 0..1) {
					val headroom = if (rise > 0) listOf(CellCondition(0, 2, 0, CellPredicate.CENTER_SLICE)) else emptyList()
					add(TemplateSpec(dx, rise, dz, MovementId.EXIT_WATER, costs.waterExit(rise), stanceConditions(dx, rise, dz) + headroom, departure = CellPredicate.WATER))
				}
			}
			for ((dx, dz) in DIAGONALS) {
				val corners = listOf(dx to 0, 0 to dz).flatMap { (x, z) ->
					listOf(CellCondition(x, 0, z, CellPredicate.SWIMMABLE), CellCondition(x, 1, z, CellPredicate.FULL_HEAD))
				}
				add(TemplateSpec(dx, 0, dz, id, costs.diagonalSwim, surface(dx, dz) + corners, departure = CellPredicate.WATER))
			}
		}
	}

	private fun openWaterLine(view: CoarseVoxelView, fromX: Double, fromZ: Double, to: Stance): Boolean {
		val toX = to.x + 0.5
		val toZ = to.z + 0.5
		val steps = ceil(hypot(toX - fromX, toZ - fromZ)).toInt()
		for (step in 1..steps) {
			val t = step.toDouble() / steps
			val x = floor(fromX + (toX - fromX) * t).toInt()
			val z = floor(fromZ + (toZ - fromZ) * t).toInt()
			if (view.medium(x, to.y, z) != Medium.WATER || !view.voxel(x, to.y + 1, z).centerPassable) return false
		}
		return true
	}

	private fun surface(dx: Int, dz: Int) = listOf(
		CellCondition(dx, 0, dz, CellPredicate.WATER),
		CellCondition(dx, 1, dz, CellPredicate.CENTER_HEAD),
	)

	private fun submergedEntry(dx: Int, dz: Int) = listOf(
		CellCondition(dx, 0, dz, CellPredicate.WATER),
		CellCondition(dx, 1, dz, CellPredicate.WATER),
	)

	override fun occupies(view: CoarseVoxelView, stance: Stance): Boolean =
		view.medium(stance.x, stance.y, stance.z) == Medium.WATER && view.voxel(stance.x, stance.y + 1, stance.z).centerPassable

	override fun decisions(context: DecisionContext): List<TrajectoryDecision> =
		context.constraints.sprintModes.map { TrajectoryDecision.Swim(context.edge.to, it, context.edge.movement) }

	override fun proposals(context: ProposalContext): List<TrajectoryDecision> {
		val leading = context.steps.firstOrNull() ?: return emptyList()
		if (leading.movement != id || !context.body.state.touchingWater) return emptyList()
		val chain = context.steering.chain(context.body.stance, leading.to, BEARING_LOOKAHEAD + 1)
		if (chain.size <= MIN_BEARING_NODES) return emptyList()
		val position = context.body.state.position
		val target = (chain.lastIndex downTo MIN_BEARING_NODES).firstOrNull { openWaterLine(context.view, position.x, position.z, chain[it]) }
			?.let { chain[it] } ?: chain[MIN_BEARING_NODES]
		val bearing = yawTowards(target.center(), context.body.state)
		val sprint = context.constraints.sprintModes.first()
		val headings = HEADING_FAN_DEGREES.map { offset -> TrajectoryDecision.Swim(leading.to, sprint, id, yaw = Rotation.wrap(bearing + offset)) }
		if (!sprint) return headings
		return listOf(TrajectoryDecision.Swim(leading.to, true, id, yaw = Rotation.wrap(bearing), stroke = true)) + headings
	}

	override fun program(context: ProgramContext): ControlProgram =
		SwimProgram(context.decision as TrajectoryDecision.Swim, context)

	override fun completed(context: CompletionContext): Boolean =
		context.stance != context.body.stance && context.stance.y == context.decision.step.y &&
			if (context.decision.movement == MovementId.EXIT_WATER) context.observed.onGround else context.observed.touchingWater

	override val inMedium: Boolean get() = true

	override fun descentAllowance(decision: TrajectoryDecision): Double = PLUNGE_ALLOWANCE

	private const val MAX_ENTRY_DEPTH = 3

	private const val BEARING_LOOKAHEAD = 64

	private const val MIN_BEARING_NODES = 2

	private val HEADING_FAN_DEGREES = listOf(0.0, -8.0, 8.0)

	private const val PLUNGE_ALLOWANCE = 2.0
}
