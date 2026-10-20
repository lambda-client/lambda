package com.lambda.pathing.actions.families

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.CompletionContext
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.DecisionPrice
import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.actions.Movement
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.StanceRules
import com.lambda.pathing.actions.TemplateSpec
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.actions.control.DropProgram
import com.lambda.pathing.actions.landingRisk
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.Medium
import kotlin.math.abs
import kotlin.math.hypot

object DropMovement : Movement {
	override val id = MovementId.DROP

	override fun templates(context: MovementContext): List<TemplateSpec> = buildList {
		val options = context.options
		for ((dx, dz) in StanceRules.CARDINALS) {
			for (depth in 2..options.maxWalkOffDepth) spec(dx, dz, depth, context)?.let(::add)
			for (span in 2..options.maxDropSpan) {
				for (depth in 1..options.maxWalkOffDepth) if (reachable(span * dx, span * dz, depth)) spec(span * dx, span * dz, depth, context)?.let(::add)
			}
		}
		if (!options.allowDiagonal) return@buildList
		for (dx in -options.maxDropSpan..options.maxDropSpan) for (dz in -options.maxDropSpan..options.maxDropSpan) {
			if (dx == 0 || dz == 0 || abs(dx) != abs(dz) && !options.allowOffAxisJumps) continue
			for (depth in 1..options.maxWalkOffDepth) if (reachable(dx, dz, depth)) spec(dx, dz, depth, context)?.let(::add)
		}
	}

	private fun reachable(dx: Int, dz: Int, depth: Int): Boolean =
		maxOf(abs(dx), abs(dz)) <= ALWAYS_REACHED_SPAN || LaunchSolver.solve(Stance(0, 0, 0), Stance(dx, 0, dz), MODES, rise = -depth - 1.0).isNotEmpty()

	private fun spec(dx: Int, dz: Int, depth: Int, context: MovementContext): TemplateSpec? = TemplateSpec(
		dx = dx, dy = -depth, dz = dz,
		movement = id,
		cost = context.costs.dropCost(hypot(dx.toDouble(), dz.toDouble()), depth) ?: return null,
		conditions = StanceRules.stanceConditions(dx, -depth, dz, CoarseVoxel.sides(-dx, -dz)) + StanceRules.leaving(CoarseVoxel.sides(dx, dz), 0) +
				StanceRules.targetCorner(dx, 0, dz, hop = false) + (if (abs(dx) > 1) StanceRules.targetCorner(dx, -depth, dz, hop = false) else emptyList()),
		arc = MotionTemplate.ArcSpec(MODES),
	)

	override fun offersFor(edge: CoarseEdge): Boolean =
		edge.to.y < edge.from.y && edge.movement !in MovementId.AQUATIC && edge.movement != MovementId.FALL && edge.movement != MovementId.NEO

	override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
		val ideal = context.edge.launch
		val asIs = LaunchSolver.solve(
			context.edge.from, context.edge.to,
			modes = MODES,
			maxEntrySpeed = { context.body.speed },
			preferredEntrySpeed = { context.body.speed },
		).firstOrNull()
		val drops = listOfNotNull(ideal, asIs)
			.filter { context.constraints.sprintModes.contains(it.sprint) }
			.distinctBy { it.mode to it.holdForward }
			.sortedWith(LaunchSolution.BEST_FIRST)
			.map { TrajectoryDecision.Drop(context.edge.to, it) }
		val from = context.edge.from
		if (context.view.medium(from.x, from.y, from.z) != Medium.CLIMBABLE) return drops
		return drops + drops.map { it.copy(climbOut = true) }
	}

	override fun price(decision: TrajectoryDecision, context: DecisionContext): DecisionPrice {
		val drop = decision as TrajectoryDecision.Drop
		return DecisionPrice(difficulty = context.landingRisk(drop.solution))
	}

	override fun program(context: ProgramContext): ControlProgram =
		DropProgram(context.decision as TrajectoryDecision.Drop, context)

	override fun completed(context: CompletionContext): Boolean = context.airborne

	private val MODES = LaunchMode.entries.filter { it.drops }

	private const val ALWAYS_REACHED_SPAN = 2
}
