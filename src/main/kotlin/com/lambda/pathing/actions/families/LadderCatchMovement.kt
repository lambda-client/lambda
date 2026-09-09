package com.lambda.pathing.actions.families

import com.lambda.pathing.actions.CellCondition
import com.lambda.pathing.actions.CellPredicate
import com.lambda.pathing.actions.CompletionContext
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.actions.Movement
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.control.SegmentFollowerProgram
import com.lambda.pathing.actions.StanceRules
import com.lambda.pathing.actions.TemplateSpec
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.actions.control.airPlanToward
import com.lambda.pathing.actions.nominalLaunchFrame
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.launch.LaunchSolver

object LadderCatchMovement : Movement {
	override val id = MovementId.LADDER_CATCH

	override fun templates(context: MovementContext): List<TemplateSpec> = buildList {
		if (!context.options.allowClimbing) return@buildList
		for ((dx, dz) in StanceRules.CARDINALS) {
			for (span in MIN_SPAN..MAX_SPAN) {
				for (rise in MIN_RISE..MAX_RISE) {
					add(
						TemplateSpec(
							dx = dx * span,
							dy = rise,
							dz = dz * span,
							movement = id,
							cost = context.costs.jumpCandidateCost(span.toDouble(), rise) +
									CATCH_SETTLE_TICKS,
							conditions = listOf(
								CellCondition(dx * span, rise, dz * span, CellPredicate.CLIMBABLE),
								CellCondition(dx * span, rise + 1, dz * span, CellPredicate.CENTER_HEAD),
							),
							arc = MotionTemplate.ArcSpec(dx * span, dz * span, rise, MODES),
						)
					)
				}
			}
		}
	}

	override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
		val edge = context.edge
		val reachable = maxOf(context.body.speed, context.ballistics.cruiseSpeed(sprint = true))

		val catches = LaunchSolver.solve(
			edge.from, edge.to,
			profile = context.ballistics,
			modes = MODES,
			maxEntrySpeed = { reachable },
			catch = true,
		)

		val solutions = (listOfNotNull(catches.firstOrNull(), edge.launch) + catches.drop(1))
			.distinctBy { it.mode to it.launchOffset }
			.filter { context.constraints.sprintModes.contains(it.sprint) }

		return solutions.flatMap { solution ->
			delays(context, solution).map { delay ->
				TrajectoryDecision.Launch(solution.sprint, edge.to, delay, solution, movement = id)
			}
		}
	}

	private fun delays(context: DecisionContext, solution: LaunchSolution): List<Int> {
		val nominal = nominalLaunchFrame(context, solution)
		if (nominal == 0) return listOf(0)
		return listOf(nominal, (nominal - 1).coerceAtLeast(0), nominal + 1).distinct()
	}

	override fun program(context: ProgramContext): ControlProgram {
		val decision = context.decision as TrajectoryDecision.Launch
		val solution = decision.solution
		val step = decision.step

		val airPlan = if (solution != null && step != null) {
			airPlanToward(context.body.stance, step, solution, lateralOffset = 0.0)
		} else null

		return SegmentFollowerProgram(
			nodes = context.nodes,
			sprint = decision.sprint,
			lookAheadNodes = 1,
			launch = context.launch,
			maxYawChange = context.constraints.maxYawDegreesPerFrame,
			holdForwardInFlight = true,
			airPlan = airPlan,
			holdJumpInFlight = true,
		)
	}

	override fun completed(context: CompletionContext): Boolean {
		val step = (context.decision as? TrajectoryDecision.Launch)?.step
			?: return context.stance != context.body.stance
		return context.launch?.hasFired != false && context.stance == step
	}

	override val completesAirborne: Boolean get() = true

	override val pressesIntoTerrain: Boolean get() = true

	override fun descentAllowance(decision: TrajectoryDecision): Double = 1.0

	private val MODES = LaunchMode.entries

	private const val MIN_SPAN = 2

	private const val MAX_SPAN = 3

	private const val MIN_RISE = -2

	private const val MAX_RISE = 1

	private const val CATCH_SETTLE_TICKS = 6.0

}
