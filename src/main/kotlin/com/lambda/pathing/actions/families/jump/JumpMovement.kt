package com.lambda.pathing.actions.families.jump

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.CompletionContext
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.DecisionPrice
import com.lambda.pathing.actions.Movement
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.TemplateSpec
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.actions.landingRisk
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.actions.control.PursuitTracker
import com.lambda.pathing.actions.control.RunUpLaunchProgram
import com.lambda.pathing.actions.control.SegmentFollowerProgram
import com.lambda.pathing.launch.AirPlan
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.core.center
import com.lambda.pathing.core.unitBetween

object JumpMovement : Movement {
	override val id = MovementId.JUMP

	override fun templates(context: MovementContext): List<TemplateSpec> =
		JumpTemplates.templates(context)

	override fun offersFor(edge: CoarseEdge): Boolean = edge.to.y >= edge.from.y

	override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
		val solutions = JumpDecisions.solutionsFor(context)
			.filter { context.constraints.sprintModes.contains(it.sprint) }
			.distinctBy { it.mode to it.launchOffset }
			.sortedByDescending { it.margin }

		val standard = solutions.flatMap { solution ->
			JumpDecisions.launchDelays(context, solution).map { delay ->
				TrajectoryDecision.Launch(context.edge.to, delay, solution)
			}
		}
		return standard + JumpDecisions.runUpDecisions(context, solutions)
	}

	override fun price(decision: TrajectoryDecision, context: DecisionContext): DecisionPrice {
		val solution =
			if (decision is TrajectoryDecision.RunUpLaunch) decision.solution else (decision as TrajectoryDecision.Launch).solution

		val tightness = 1.0 - (solution.speedSlack / COMFORTABLE_SPEED_SLACK).coerceIn(0.0, 1.0)
		val base = DecisionPrice(
			difficulty = maxOf(tightness, context.landingRisk(solution)),
		)
		if (decision !is TrajectoryDecision.RunUpLaunch) return base
		return base + DecisionPrice(
			ticks = transitionFrames(decision).toDouble(),
			difficulty = RUN_UP_DIFFICULTY,
		)
	}

	override fun program(context: ProgramContext): ControlProgram {
		val decision = context.decision
		if (decision is TrajectoryDecision.RunUpLaunch) {
			val from = context.body.stance.center()
			val to = decision.step.center().laterallyShifted(from, decision.solution.lateralOffset)
			return RunUpLaunchProgram(
				takeoff = from,
				aim = to,
				solution = decision.solution,
				retreatAlong = decision.retreatAlong,
				hopAlong = decision.hopAlong,
				maxYawChange = context.constraints.maxYawDegreesPerFrame,
			)
		}
		val launch = decision as TrajectoryDecision.Launch
		val solution = launch.solution
		val step = launch.step
		val nodes = if (solution.lateralOffset != 0.0) {
			val from = context.body.stance.center()
			context.nodes.map { node ->
				if (node.x == step.x + 0.5 && node.z == step.z + 0.5) {
					node.laterallyShifted(from, solution.lateralOffset)
				} else node
			}
		} else context.nodes
		return SegmentFollowerProgram(
			nodes = nodes,
			sprint = solution.sprint,
			lookAheadNodes = PursuitTracker.DEFAULT_LOOK_AHEAD_NODES,
			launch = context.launch,
			maxYawChange = context.constraints.maxYawDegreesPerFrame,
			holdTicks = solution.heldForwardTicks,
			airPlan = if (context.openLanding) null else AirPlan.toward(context.body.stance, step, solution, solution.lateralOffset),
		)
	}

	private fun HorizontalPoint.laterallyShifted(
		from: HorizontalPoint,
		offset: Double,
	): HorizontalPoint {
		if (offset == 0.0) return this
		val (unitX, unitZ) = unitBetween(from, this) ?: return this
		return copy(x = x - unitZ * offset, z = z + unitX * offset)
	}

	override fun completed(context: CompletionContext): Boolean {
		val decision = context.decision
		if (decision is TrajectoryDecision.RunUpLaunch) {
			if (!context.airborne) return false
			val from = context.body.stance.center()
			val to = decision.step.center()
			val along = alongEdge(from, to, context.observed.position.x, context.observed.position.z)
			return along > decision.solution.launchOffset + POST_LAUNCH_MARGIN_BLOCKS
		}
		return context.launch?.hasFired == true && context.airborne
	}

	override fun transitionFrames(decision: TrajectoryDecision): Int {
		val runUp = decision as? TrajectoryDecision.RunUpLaunch ?: return 0
		val retreatFrames = (-runUp.retreatAlong).coerceAtLeast(0.0) * RETREAT_FRAMES_PER_BLOCK
		return RUN_UP_TRANSITION_FRAMES + retreatFrames.toInt() + runUp.solution.airTicks
	}

	private const val RUN_UP_TRANSITION_FRAMES = 50

	private const val RETREAT_FRAMES_PER_BLOCK = 10

	private const val POST_LAUNCH_MARGIN_BLOCKS = 1.0

	private const val COMFORTABLE_SPEED_SLACK = 0.08

	private const val RUN_UP_DIFFICULTY = 0.75
}
