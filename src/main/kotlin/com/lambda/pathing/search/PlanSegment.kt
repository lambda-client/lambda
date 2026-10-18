package com.lambda.pathing.search

import com.lambda.pathing.core.Stance
import com.lambda.pathing.actions.MotionConstraints
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

	val settledExit: Boolean
		get() = exit.onGround && exit.velocity.horizontalLength() > MotionConstraints.STOPPED_SPEED

	class Move(
		val decision: TrajectoryDecision,
		override val entry: MovementSimulationState,
		override val exit: MovementSimulationState,
		override val inputs: List<MovementSimulationInput>,
		override val startFrame: Int,
		/** The stances the decision steered by, so a replay of it does not depend on the labels of the moment. */
		val chain: List<Stance> = emptyList(),
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

internal fun List<PlanSegment>.repairFrame(firstAffectedFrame: Int, cursorFrame: Int, minLeadFrames: Int): Int? =
	lastOrNull { it.exit.onGround && it.endFrame < firstAffectedFrame && it.endFrame >= cursorFrame + minLeadFrames }?.endFrame
