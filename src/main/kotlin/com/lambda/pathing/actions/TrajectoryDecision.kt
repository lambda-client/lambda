package com.lambda.pathing.actions

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.MovementKeys
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.Via
import com.lambda.pathing.launch.BounceSolution
import com.lambda.pathing.launch.LaunchSolution

interface TrajectoryDecision {

	val movement: MovementId

	val step: Stance

	val delayFrames: Int? get() = null

	val seedsHazardOnFailure: Boolean get() = false

	val leavesGround: Boolean get() = false

	data class Walk(
		val sprint: Boolean,
		override val step: Stance,
		val lookAheadNodes: Int,
		val easeTurns: Boolean,
		override val movement: MovementId = MovementId.WALK,
		val via: Via? = null,
	) : TrajectoryDecision {
		override val seedsHazardOnFailure: Boolean get() = true
	}

	data class Launch(
		override val step: Stance,
		override val delayFrames: Int,
		val solution: LaunchSolution,
		override val movement: MovementId = MovementId.JUMP,
	) : TrajectoryDecision

	data class Drop(
		override val step: Stance,
		val solution: LaunchSolution,
		val climbOut: Boolean = false,
		override val movement: MovementId = MovementId.DROP,
	) : TrajectoryDecision {
		override val leavesGround: Boolean get() = true
	}

	data class Climb(
		override val step: Stance,
		val holdForward: Boolean,
		val holdWhileClimbing: Boolean,
		val climbWithJump: Boolean,
		val yaw: Double?,
		override val movement: MovementId = MovementId.CLIMB,
	) : TrajectoryDecision

	data class Heading(
		val sprint: Boolean,
		override val step: Stance,
		val yaw: Double,
		override val delayFrames: Int?,
		val keys: MovementKeys = MovementKeys.FORWARD,
		val airborneKeys: MovementKeys = keys,
		override val movement: MovementId = MovementId.WALK,
		val turnRate: Double = Double.POSITIVE_INFINITY,
	) : TrajectoryDecision

	data class RunUpLaunch(
		override val step: Stance,
		val solution: LaunchSolution,
		val retreatAlong: Double,
		val hopAlong: Double?,
		override val movement: MovementId = MovementId.JUMP,
	) : TrajectoryDecision

	data class Bounce(
		override val step: Stance,
		val solution: BounceSolution,
		override val movement: MovementId = MovementId.BOUNCE,
	) : TrajectoryDecision

	data class Fall(
		override val step: Stance,
		val solution: LaunchSolution,
		val water: Boolean,
		val plunge: Double,
		val plungeTicks: Int,
		override val movement: MovementId = MovementId.FALL,
		val swimOn: Boolean = false,
	) : TrajectoryDecision {
		override val leavesGround: Boolean get() = true
	}

	data class Dive(
		override val step: Stance,
		override val movement: MovementId = MovementId.DIVE,
		val breathe: Boolean = false,
		val detour: Boolean = false,
		val surface: Boolean = false,
		val aim: Stance = step,
		val reach: Int = 1,
	) : TrajectoryDecision

	data class Swim(
		override val step: Stance,
		val sprint: Boolean,
		override val movement: MovementId = MovementId.SWIM,
		val yaw: Double? = null,
		val stroke: Boolean = false,
	) : TrajectoryDecision
}
