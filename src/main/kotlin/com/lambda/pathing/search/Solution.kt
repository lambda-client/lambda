package com.lambda.pathing.search

import com.lambda.pathing.rollout.SimulatedTrajectoryFrame

import com.lambda.pathing.physics.MovementSimulationInput

internal class Solution(
	val frames: Int,
	val collisionEvents: Int,
	val anchor: ValueAnchor,
	val tailFrames: List<SimulatedTrajectoryFrame>,
) {

	val planSegments: List<PlanSegment> by lazy { segmentsOf(anchor, tailFrames) }

	val inputs: List<MovementSimulationInput> by lazy { planSegments.flatMap { it.inputs } }

	val score: Int get() = frames + COLLISION_FRAME_PENALTY * collisionEvents

	companion object {

		internal const val COLLISION_FRAME_PENALTY = 4

		private fun segmentsOf(anchor: ValueAnchor, tail: List<SimulatedTrajectoryFrame>): List<PlanSegment> {
			val chain = ArrayList<ValueAnchor>()
			var node: ValueAnchor? = anchor
			while (node?.parent != null) {
				chain += node
				node = node.parent
			}
			chain.reverse()

			val segments = ArrayList<PlanSegment>(chain.size + 1)
			chain.forEach { child ->
				val parent = child.parent ?: return@forEach
				val decision = child.decision
				segments += if (decision != null) {
					PlanSegment.Move(decision, parent.state, child.state, child.inputs, parent.elapsed)
				} else {
					PlanSegment.Terminal(parent.state, child.state, child.inputs, parent.elapsed)
				}
			}
			if (tail.isNotEmpty()) {
				segments += PlanSegment.Terminal(anchor.state, tail.last().state, tail.map { it.input }, anchor.elapsed)
			}
			return segments
		}

		fun of(anchor: ValueAnchor, tail: List<SimulatedTrajectoryFrame>, collisionEvents: Int): Solution =
			Solution(anchor.elapsed + tail.size, collisionEvents, anchor, tail)
	}
}
