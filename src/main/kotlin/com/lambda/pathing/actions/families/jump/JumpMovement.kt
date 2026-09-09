package com.lambda.pathing.actions.families.jump

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.CompletionContext
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.DecisionPrice
import com.lambda.pathing.actions.Movement
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.ProposalContext
import com.lambda.pathing.actions.Proposals
import com.lambda.pathing.actions.TemplateSpec
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.actions.landingRisk
import com.lambda.pathing.core.MovementId

object JumpMovement : Movement {
	override val id = MovementId.JUMP

	override fun templates(context: MovementContext): List<TemplateSpec> =
		JumpTemplates.templates(context, id)

	override fun offersFor(edge: CoarseEdge): Boolean = edge.to.y >= edge.from.y

	override fun proposals(context: ProposalContext): Proposals = MomentumProposals.proposals(context)

	override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
		val solutions = JumpDecisions.solutionsFor(context)
			.filter { context.constraints.sprintModes.contains(it.sprint) }
			.distinctBy { it.mode to it.launchOffset }
			.sortedByDescending { it.margin }

		val closing = JumpDecisions.closingSpeed(context)
		val standard = solutions.flatMap { solution ->
			JumpLaunchDelays.launchDelays(context, solution).map { delay ->
				TrajectoryDecision.Launch(solution.sprint, context.edge.to, delay, solution)
			}
		}
		return standard + JumpDecisions.runUpDecisions(context, solutions, closing)
	}

	override fun price(decision: TrajectoryDecision, context: DecisionContext): DecisionPrice {
		val solution = when (decision) {
			is TrajectoryDecision.Launch -> decision.solution
			is TrajectoryDecision.RunUpLaunch -> decision.solution
			else -> null
		}
		if (solution == null) return DecisionPrice.FREE

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

	override fun program(context: ProgramContext): ControlProgram = JumpPrograms.program(context)

	override fun completed(context: CompletionContext): Boolean = JumpPrograms.completed(context)

	override fun transitionFrames(decision: TrajectoryDecision): Int = JumpPrograms.transitionFrames(decision)

	private const val COMFORTABLE_SPEED_SLACK = 0.08

	private const val RUN_UP_DIFFICULTY = 0.75
}
