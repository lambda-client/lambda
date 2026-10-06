package com.lambda.pathing.search

import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState

sealed interface PlanSegment {
	val entry: MovementSimulationState
	val exit: MovementSimulationState
	val inputs: List<MovementSimulationInput>

	val startFrame: Int

	val movement: MovementId

	val frameCount: Int get() = inputs.size

	val endFrame: Int get() = startFrame + inputs.size

	fun settledExit(stoppedSpeed: Double): Boolean =
		exit.onGround && exit.velocity.horizontalLength() > stoppedSpeed

	class Move(
		val decision: TrajectoryDecision,
		override val entry: MovementSimulationState,
		override val exit: MovementSimulationState,
		override val inputs: List<MovementSimulationInput>,
		override val startFrame: Int,
	) : PlanSegment {
		override val movement: MovementId get() = decision.movement
	}

	class Terminal(
		override val entry: MovementSimulationState,
		override val exit: MovementSimulationState,
		override val inputs: List<MovementSimulationInput>,
		override val startFrame: Int,
	) : PlanSegment {
		override val movement: MovementId get() = MovementId.WALK
	}
}
