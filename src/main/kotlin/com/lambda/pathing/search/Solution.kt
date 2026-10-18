package com.lambda.pathing.search

import com.lambda.pathing.rollout.SimulatedTrajectoryFrame

import com.lambda.pathing.physics.MovementSimulationInput

internal class Solution(
	val anchor: ValueAnchor,
	val tailFrames: List<SimulatedTrajectoryFrame>,
) {
	val collisionEvents: Int = anchor.collisionEvents + collisionEvents(anchor.state, tailFrames)

	val frames: Int get() = anchor.elapsed + tailFrames.size

	val planSegments: List<PlanSegment> by lazy { segmentsOf(anchor, tailFrames) }

	val inputs: List<MovementSimulationInput> by lazy { planSegments.flatMap { it.inputs } }

	val score: Int get() = frames

	fun beats(other: Solution): Boolean = frames < other.frames || frames == other.frames && collisionEvents < other.collisionEvents

	companion object {

		private fun segmentsOf(anchor: ValueAnchor, tail: List<SimulatedTrajectoryFrame>): List<PlanSegment> {
			val segments = anchor.lineFrom(null).mapTo(ArrayList()) { child ->
				val parent = checkNotNull(child.parent)
				val decision = child.decision
				if (decision != null) PlanSegment.Move(decision, parent.state, child.state, child.inputs, parent.elapsed, child.chain)
				else PlanSegment.Terminal(parent.state, child.state, child.inputs, parent.elapsed)
			}
			if (tail.isNotEmpty()) {
				segments += PlanSegment.Terminal(anchor.state, tail.last().state, tail.map { it.input }, anchor.elapsed)
			}
			return segments
		}
	}
}
