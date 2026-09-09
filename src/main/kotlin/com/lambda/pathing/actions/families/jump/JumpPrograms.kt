package com.lambda.pathing.actions.families.jump

import com.lambda.pathing.actions.CompletionContext
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.control.PursuitTracker
import com.lambda.pathing.actions.control.RunUpLaunchProgram
import com.lambda.pathing.actions.control.SegmentFollowerProgram
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.actions.control.airPlanToward
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.core.center
import com.lambda.pathing.launch.AirSteering
import com.lambda.pathing.launch.LaunchSolution

internal object JumpPrograms {

	fun program(context: ProgramContext): ControlProgram {
		val decision = context.decision
		if (decision is TrajectoryDecision.RunUpLaunch) {
			val from = context.body.stance.center()
			val to = (decision.step ?: context.body.stance).center()
				.laterallyShifted(from, decision.solution.lateralOffset)
			return RunUpLaunchProgram(
				takeoff = from,
				aim = to,
				solution = decision.solution,
				retreatAlong = decision.retreatAlong,
				hopAlong = decision.hopAlong,
				maxYawChange = context.constraints.maxYawDegreesPerFrame,
			)
		}
		val solution = (decision as? TrajectoryDecision.Launch)?.solution

		val step = (decision as? TrajectoryDecision.Launch)?.step
		val nodes = if (solution != null && solution.lateralOffset != 0.0 && step != null) {
			val from = context.body.stance.center()
			context.nodes.map { node ->
				if (node.x == step.x + 0.5 && node.z == step.z + 0.5) {
					node.laterallyShifted(from, solution.lateralOffset)
				} else node
			}
		} else context.nodes
		return SegmentFollowerProgram(
			nodes = nodes,
			sprint = context.decision.sprint,
			lookAheadNodes = PursuitTracker.DEFAULT_LOOK_AHEAD_NODES,
			launch = context.launch,
			maxYawChange = context.constraints.maxYawDegreesPerFrame,
			holdForwardInFlight = solution?.holdForward ?: true,
			holdTicks = solution?.holdTicks ?: Int.MAX_VALUE,
			airPlan = if (context.openLanding) null
			else airPlanFor(context.body.stance, (decision as? TrajectoryDecision.Launch)?.step, solution),
		)
	}

	private fun airPlanFor(from: Stance, to: Stance?, solution: LaunchSolution?): AirSteering.AirPlan? {
		if (solution == null || to == null || to == from) return null
		return airPlanToward(from, to, solution, solution.lateralOffset)
	}

	private fun HorizontalPoint.laterallyShifted(
		from: HorizontalPoint,
		offset: Double,
	): HorizontalPoint {
		if (offset == 0.0) return this
		val length = kotlin.math.hypot(x - from.x, z - from.z)
		if (length <= 1e-9) return this
		val unitX = (x - from.x) / length
		val unitZ = (z - from.z) / length
		return copy(x = x - unitZ * offset, z = z + unitX * offset)
	}

	fun completed(context: CompletionContext): Boolean {
		val decision = context.decision
		if (decision is TrajectoryDecision.RunUpLaunch) {
			if (!context.airborne) return false
			val from = context.body.stance.center()
			val to = (decision.step ?: context.body.stance).center()
			val along = alongEdge(from, to, context.observed.position.x, context.observed.position.z)
			return along > decision.solution.launchOffset + POST_LAUNCH_MARGIN_BLOCKS
		}
		val launch = context.launch ?: return context.stance != context.body.stance
		return launch.hasFired && context.airborne
	}

	fun transitionFrames(decision: TrajectoryDecision): Int {
		val runUp = decision as? TrajectoryDecision.RunUpLaunch ?: return 0
		val retreatFrames = (-runUp.retreatAlong).coerceAtLeast(0.0) * RETREAT_FRAMES_PER_BLOCK
		return RUN_UP_TRANSITION_FRAMES + retreatFrames.toInt() + runUp.solution.airTicks
	}

	private const val RUN_UP_TRANSITION_FRAMES = 50

	private const val RETREAT_FRAMES_PER_BLOCK = 10

	private const val POST_LAUNCH_MARGIN_BLOCKS = 1.0
}
