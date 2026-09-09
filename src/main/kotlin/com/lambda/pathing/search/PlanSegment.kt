package com.lambda.pathing.search

import com.lambda.pathing.actions.TerminalApproach
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState

sealed interface PlanSegment {
	val entry: MovementSimulationState
	val exit: MovementSimulationState
	val inputs: List<MovementSimulationInput>

	val startFrame: Int

	val frameCount: Int get() = inputs.size

	val endFrame: Int get() = startFrame + inputs.size

	fun settledExit(stoppedSpeed: Double): Boolean =
		exit.onGround && exit.velocity.horizontalLength() > stoppedSpeed

	fun at(startFrame: Int): PlanSegment

	class Move(
		val decision: TrajectoryDecision,
		val points: List<HorizontalPoint>,
		override val entry: MovementSimulationState,
		override val exit: MovementSimulationState,
		override val inputs: List<MovementSimulationInput>,
		override val startFrame: Int,
	) : PlanSegment {
		override fun at(startFrame: Int) =
			Move(decision, points, entry, exit, inputs, startFrame)
	}

	class Terminal(
		val approach: TerminalApproach,
		override val entry: MovementSimulationState,
		override val exit: MovementSimulationState,
		override val inputs: List<MovementSimulationInput>,
		override val startFrame: Int,
	) : PlanSegment {
		override fun at(startFrame: Int) =
			Terminal(approach, entry, exit, inputs, startFrame)
	}
}
