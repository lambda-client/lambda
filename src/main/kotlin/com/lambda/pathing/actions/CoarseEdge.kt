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

	/**
	 * Whether a body at rest on [from] can execute this edge with only the run-up its own
	 * cell (and one walkable cell behind it) affords. False for launches that need
	 * momentum carried in from earlier movement; the STOPPED class never departs on those.
	 * See docs/decisions/movement-tuning.md (standing starts).
	 */
	val standingStart: Boolean = true,
)
