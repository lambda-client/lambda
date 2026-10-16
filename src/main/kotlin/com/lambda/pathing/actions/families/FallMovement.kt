package com.lambda.pathing.actions.families

import com.lambda.pathing.actions.CompletionContext
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.DecisionPrice
import com.lambda.pathing.actions.Movement
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.actions.TemplateSpec
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.actions.control.FallProgram
import com.lambda.pathing.actions.landingRisk
import com.lambda.pathing.actions.reachableEntrySpeed
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.launch.FallReach
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.world.Medium
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

object FallMovement : Movement {
	override val id = MovementId.FALL

	override fun templates(context: MovementContext): List<TemplateSpec> = emptyList()

	override fun scans(options: SimpleMoveOptions): Boolean = options.allowDeepFalls

	override val inMedium: Boolean get() = true

	override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
		val edge = context.edge
		val view = context.view
		val from = edge.from
		val to = edge.to
		if (view.medium(from.x, from.y, from.z) == Medium.WATER) {
			return listOf(TrajectoryDecision.Dive(to, surface = !DiveMovement.submerged(view, to.x, to.y, to.z), aim = to, reach = from.y - to.y))
		}
		val water = view.medium(to.x, to.y, to.z) == Medium.WATER
		val launch = from.y + view.surfaceOffset(from.x, from.y - 1, from.z)
		val landing = if (water) to.y + 1.0 else to.y + view.surfaceOffset(to.x, to.y - 1, to.z)
		val rise = landing - launch
		val cells = if (water) waterCells(context) else 0
		val modes = LaunchMode.entries.filter { it.sprint in context.constraints.sprintModes }
		val entry = reachableEntrySpeed(context)
		val swimOn = water && openWaterBeyond(context)
		val falls = LaunchSolver.solve(
			from, to,
			modes = modes,
			maxEntrySpeed = { entry },
			preferredEntrySpeed = { entry },
			rise = rise,
			maxTicks = FallReach.MAX_FALL_TICKS,
		).map { solution ->
			val plunge = if (!water) null else FallReach.flight(solution.jumps, -rise)?.let { FallReach.plunge(it.impactSpeed, cells) }
			TrajectoryDecision.Fall(to, solution, water, plunge?.depth ?: 0.0, plunge?.ticks ?: 0, swimOn = swimOn)
		}
		if (!water || falls.isNotEmpty()) return falls
		return context.constraints.sprintModes.map { TrajectoryDecision.Swim(to, it, MovementId.ENTER_WATER) }
	}

	private fun openWaterBeyond(context: DecisionContext): Boolean {
		val from = context.edge.from
		val to = context.edge.to
		val length = hypot((to.x - from.x).toDouble(), (to.z - from.z).toDouble())
		if (length == 0.0) return false
		val x = floor(to.x + 0.5 + (to.x - from.x) / length).toInt()
		val z = floor(to.z + 0.5 + (to.z - from.z) / length).toInt()
		return context.view.medium(x, to.y, z) == Medium.WATER
	}

	private fun waterCells(context: DecisionContext): Int {
		val to = context.edge.to
		var cells = 0
		while (cells < FallReach.MAX_WATER_CELLS && context.view.medium(to.x, to.y - cells, to.z) == Medium.WATER) cells++
		return cells
	}

	override fun price(decision: TrajectoryDecision, context: DecisionContext): DecisionPrice {
		val fall = decision as TrajectoryDecision.Fall
		return DecisionPrice(difficulty = if (fall.water) 0.0 else context.landingRisk(fall.solution))
	}

	override fun program(context: ProgramContext): ControlProgram =
		FallProgram(context.decision as TrajectoryDecision.Fall, context)

	override fun completed(context: CompletionContext): Boolean {
		if (!context.airborne) return false
		val fall = context.decision as TrajectoryDecision.Fall
		val step = fall.step
		if (abs(context.stance.x - step.x) > LANDING_SLACK || abs(context.stance.z - step.z) > LANDING_SLACK) return false
		if (!fall.water) return context.stance.y == step.y && context.observed.onGround
		return context.observed.touchingWater && context.stance.y <= step.y && context.stance.y >= step.y - ceil(fall.plunge).toInt()
	}

	override fun descentAllowance(decision: TrajectoryDecision): Double {
		val fall = decision as TrajectoryDecision.Fall
		return if (fall.water) fall.plunge + PLUNGE_MARGIN else 0.0
	}

	override fun transitionFrames(decision: TrajectoryDecision): Int {
		val fall = decision as TrajectoryDecision.Fall
		return fall.solution.airTicks + fall.plungeTicks + APPROACH_FRAMES
	}

	private const val LANDING_SLACK = 1

	private const val PLUNGE_MARGIN = 1.0

	private const val APPROACH_FRAMES = 30
}
