package com.lambda.pathing.actions

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.launch.BounceSolution
import com.lambda.pathing.launch.LaunchSolution

@JvmInline
value class MotionTemplateId(val value: Int)

data class CoarseEdgeId(val template: MotionTemplateId, val from: Stance)

class LazyReadSet(supplier: () -> Set<VoxelPos>) : AbstractSet<VoxelPos>() {
	private val backing: Set<VoxelPos> by lazy(LazyThreadSafetyMode.PUBLICATION, supplier)

	override val size: Int get() = backing.size

	override fun iterator(): Iterator<VoxelPos> = backing.iterator()

	override fun contains(element: VoxelPos): Boolean = element in backing
}

data class CoarseEdge(
	val id: CoarseEdgeId,
	val from: Stance,
	val to: Stance,
	val movement: MovementId,
	val lowerBoundTicks: Double,
	val readSet: Set<VoxelPos>,

	val launch: LaunchSolution? = null,

	val bounce: BounceSolution? = null,

	val standingStart: Boolean = true,
)
