package com.lambda.pathing.actions

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.Via
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.launch.BounceSolution
import com.lambda.pathing.launch.LaunchSolution

@JvmInline
value class MotionTemplateId(val value: Int)

data class CoarseEdge(
	val template: MotionTemplateId,
	val from: Stance,
	val to: Stance,
	val movement: MovementId,
	val lowerBoundTicks: Double,
	val readSet: Set<VoxelPos>,
	val launch: LaunchSolution? = null,
	val bounce: BounceSolution? = null,
	val standingStart: Boolean = true,
	val via: Via? = null,
)
