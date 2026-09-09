package com.lambda.pathing.actions

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.MovementKeys
import com.lambda.pathing.core.Stance
import com.lambda.pathing.launch.BounceSolution
import com.lambda.pathing.launch.LaunchSolution

interface TrajectoryDecision {

	val movement: MovementId

	val sprint: Boolean

	val step: Stance?

	val margin: Double get() = 0.0

	val launchDelayFrames: Int? get() = null

	val family: Any? get() = null

	val seedsHazardOnFailure: Boolean get() = false

	val leavesGround: Boolean get() = false

	val spansEdges: Boolean get() = false

	val effect: WorldEffect? get() = null

	val followLookAheadNodes: Int? get() = null

	data class Walk(
		override val sprint: Boolean,
		override val step: Stance?,
		val lookAheadNodes: Int,
		val easeTurns: Boolean,
		override val movement: MovementId = MovementId.WALK,
	) : TrajectoryDecision {
		override val followLookAheadNodes: Int get() = lookAheadNodes
		override val seedsHazardOnFailure: Boolean get() = true
	}

	data class Launch(
		override val sprint: Boolean,
		override val step: Stance?,
		val delayFrames: Int,
		val solution: LaunchSolution? = null,
		override val movement: MovementId = MovementId.JUMP,
	) : TrajectoryDecision {
		override val margin: Double get() = solution?.margin ?: 0.0

		override val launchDelayFrames: Int get() = delayFrames

		override val family: Any get() = DecisionFamily(movement, sprint, step, null, null, null)

		override val spansEdges: Boolean get() = solution == null && delayFrames == 0
	}

	data class Drop(
		override val sprint: Boolean,
		override val step: Stance?,
		val solution: LaunchSolution,
		override val movement: MovementId = MovementId.DROP,
	) : TrajectoryDecision {
		override val margin: Double get() = solution.margin

		override val leavesGround: Boolean get() = true
	}

	data class Climb(
		override val step: Stance?,

		val holdForward: Boolean,

		val holdWhileClimbing: Boolean,

		val climbWithJump: Boolean,

		val yaw: Double?,
		override val sprint: Boolean = false,
		override val movement: MovementId = MovementId.CLIMB,
	) : TrajectoryDecision

	data class Heading(
		override val sprint: Boolean,
		override val step: Stance?,
		val yaw: Double,
		val delayFrames: Int?,
		val keys: MovementKeys = MovementKeys.FORWARD,
		val airborneKeys: MovementKeys = keys,
		override val movement: MovementId = MovementId.WALK,
	) : TrajectoryDecision {
		override val launchDelayFrames: Int? get() = delayFrames

		override val family: Any get() = DecisionFamily(movement, sprint, step, yaw, keys, airborneKeys)
	}

	data class RunUpLaunch(
		override val sprint: Boolean,
		override val step: Stance?,
		val solution: LaunchSolution,
		val retreatAlong: Double,
		val hopAlong: Double?,
		override val movement: MovementId = MovementId.JUMP,
	) : TrajectoryDecision {
		override val margin: Double get() = solution.margin
	}

	data class Bounce(
		override val sprint: Boolean,
		override val step: Stance?,
		val solution: BounceSolution,
		override val movement: MovementId = MovementId.BOUNCE,
	) : TrajectoryDecision {

		override val margin: Double get() = solution.speedSlack
	}
}

data class DecisionFamily(
	val movement: MovementId,
	val sprint: Boolean,
	val step: Stance?,
	val yaw: Double?,
	val keys: MovementKeys?,
	val airborneKeys: MovementKeys?,
)
