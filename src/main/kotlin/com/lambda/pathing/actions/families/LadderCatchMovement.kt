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
import com.lambda.pathing.actions.StanceRules
import com.lambda.pathing.actions.TemplateSpec
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.actions.control.SegmentFollowerProgram
import com.lambda.pathing.actions.nominalLaunchFrame
import com.lambda.pathing.actions.reachableEntrySpeed
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.launch.AirPlan
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.launch.LaunchSolver
import kotlin.math.abs
import kotlin.math.hypot

object LadderCatchMovement : Movement {
	override val id = MovementId.LADDER_CATCH

	override fun templates(context: MovementContext): List<TemplateSpec> = buildList {
		val options = context.options
		if (!options.allowClimbing) return@buildList
		val span = maxOf(options.maxJumpSpan, MIN_SPAN)
		for (dx in -span..span) {
			for (dz in -span..span) {
				if (maxOf(abs(dx), abs(dz)) < MIN_SPAN) continue
				if (dx != 0 && dz != 0 && !options.allowDiagonal) continue
				if (abs(dx) != abs(dz) && dx != 0 && dz != 0 && !options.allowOffAxisJumps) continue
				val gap = hypot((abs(dx) - 1).toDouble(), (abs(dz) - 1).toDouble())
				for (rise in MIN_RISE..MAX_RISE) {
					if (gap > catchGap(rise, options.allowDeepDropJumps) + REACH_EPSILON) continue
					val cost = context.costs.jumpCandidateCost(hypot(dx.toDouble(), dz.toDouble()), rise) ?: continue
					add(
						TemplateSpec(
							dx = dx,
							dy = rise,
							dz = dz,
							movement = id,
							cost = cost,
							conditions = listOf(
								CellCondition(dx, rise, dz, CellPredicate.CLIMBABLE),
								CellCondition(dx, rise + 1, dz, CellPredicate.CENTER_HEAD),
							) + StanceRules.targetCorner(dx, maxOf(0, rise), dz, hop = true),
							departure = CellPredicate.LAUNCHABLE,
							arc = MotionTemplate.ArcSpec(),
						)
					)
				}
			}
		}
	}

	override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
		val edge = context.edge
		val reachable = reachableEntrySpeed(context)

		val catches = LaunchSolver.solve(
			edge.from, edge.to,
			maxEntrySpeed = { reachable },
			catch = true,
		)

		val solutions = (listOfNotNull(catches.firstOrNull(), edge.launch) + catches.drop(1))
			.distinctBy { it.mode to it.launchOffset }
			.filter { context.constraints.sprintModes.contains(it.sprint) }

		return solutions.flatMap { solution ->
			delays(context, solution).map { delay ->
				TrajectoryDecision.Launch(edge.to, delay, solution, movement = id)
			}
		}
	}

	private fun delays(context: DecisionContext, solution: LaunchSolution): List<Int> {
		val nominal = nominalLaunchFrame(context, solution)
		if (nominal == 0) return listOf(0)
		return listOf(nominal, nominal - 1, nominal + 1)
	}

	override fun program(context: ProgramContext): ControlProgram {
		val decision = context.decision as TrajectoryDecision.Launch
		val airPlan = AirPlan.toward(context.body.stance, decision.step, decision.solution, lateralOffset = 0.0)

		return SegmentFollowerProgram(
			nodes = context.nodes,
			sprint = decision.solution.sprint,
			lookAheadNodes = 1,
			launch = context.launch,
			turning = context.constraints.turning,
			airPlan = airPlan,
			holdJumpInFlight = true,
		)
	}

	override fun completed(context: CompletionContext): Boolean =
		context.launch?.hasFired != false && context.stance == context.decision.step

	override val inMedium: Boolean get() = true

	override fun descentAllowance(decision: TrajectoryDecision): Double = 1.0

	private fun catchGap(rise: Int, deepDrops: Boolean): Double = when {
		rise >= 1 -> RISING_CATCH_GAP_BLOCKS
		rise < 0 && deepDrops -> DROPPING_CATCH_GAP_BLOCKS
		else -> LEVEL_CATCH_GAP_BLOCKS
	}

	private const val MIN_SPAN = 2

	private const val REACH_EPSILON = 1e-9

	private val RISING_CATCH_GAP_BLOCKS = hypot(2.0, 2.0)

	private val LEVEL_CATCH_GAP_BLOCKS = hypot(3.0, 1.0)

	private val DROPPING_CATCH_GAP_BLOCKS = hypot(3.0, 3.0)

	private const val MIN_RISE = -2

	private const val MAX_RISE = 1
}
