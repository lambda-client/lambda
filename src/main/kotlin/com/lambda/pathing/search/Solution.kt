package com.lambda.pathing.search

import com.lambda.pathing.rollout.SimulatedTrajectoryFrame

import com.lambda.pathing.actions.TerminalApproach
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.physics.MovementSimulationInput

data class TapeSegment(val movement: MovementId, val frames: Int)

internal class Solution(
	val segments: Int,
	val launchMargin: Int,
	val parameters: TerminalApproach,
	val frames: Int,
	val collisionEvents: Int,
	val anchor: ValueAnchor,
	val tailFrames: List<SimulatedTrajectoryFrame>,
) {

	val airborneCollisionEvents: Int by lazy { anchor.airborneCollisionEvents + airborneCollisionEvents(anchor.state, tailFrames) }

	val planSegments: List<PlanSegment> by lazy { segmentsOf(anchor, tailFrames, parameters) }

	val inputs: List<MovementSimulationInput> by lazy { anchor.prefix() + tailFrames.map { it.input } }

	val boundaries: List<Int> by lazy { anchor.boundaries() + anchor.elapsed }

	val score: Int get() = frames + COLLISION_FRAME_PENALTY * collisionEvents

	fun segments(): List<TapeSegment> {
		val out = ArrayList<TapeSegment>()
		var node: ValueAnchor? = anchor
		while (node?.parent != null) {
			out += TapeSegment(node.via ?: MovementId.WALK, node.inputs.size)
			node = node.parent
		}
		out.reverse()
		val tail = frames - out.sumOf { it.frames }
		if (tail > 0) out += TapeSegment(MovementId.WALK, tail)
		return out
	}

	companion object {

		internal const val COLLISION_FRAME_PENALTY = 4

		internal fun segmentsOf(
			anchor: ValueAnchor,
			tail: List<SimulatedTrajectoryFrame>,
			parameters: TerminalApproach,
		): List<PlanSegment> {
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
					PlanSegment.Move(
						decision = decision,
						points = child.points,
						entry = parent.state,
						exit = child.state,
						inputs = child.inputs,
						startFrame = parent.elapsed,
					)
				} else {
					PlanSegment.Terminal(
						approach = parameters,
						entry = parent.state,
						exit = child.state,
						inputs = child.inputs,
						startFrame = parent.elapsed,
					)
				}
			}
			if (tail.isNotEmpty()) {
				segments += PlanSegment.Terminal(
					approach = parameters,
					entry = anchor.state,
					exit = tail.last().state,
					inputs = tail.map { it.input },
					startFrame = anchor.elapsed,
				)
			}
			return segments
		}

		fun of(
			anchor: ValueAnchor,
			tail: List<SimulatedTrajectoryFrame>,
			parameters: TerminalApproach,
			collisionEvents: Int,
		): Solution = Solution(
			segments = anchor.depth() + 1,
			launchMargin = anchor.launchMargin,
			parameters = parameters,
			frames = anchor.elapsed + tail.size,
			collisionEvents = collisionEvents,
			anchor = anchor,
			tailFrames = tail,
		)
	}
}
