package com.lambda.pathing.search

import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.rollout.SimulatedTrajectoryFrame
import net.minecraft.util.math.BlockPos

internal class Solution(
	val anchor: ValueAnchor,
	val tailFrames: List<SimulatedTrajectoryFrame>,
	closing: Boolean = false,
) {
	val collisionEvents: Int = anchor.collisionEvents + collisionEvents(anchor.state, tailFrames)

	val frames: Int get() = anchor.elapsed + tailFrames.size

	val unrestored: Int = if (closing) unrestoredOf(anchor, tailFrames) else 0

	val planSegments: List<PlanSegment> by lazy { segmentsOf(anchor, tailFrames) }

	val inputs: List<MovementSimulationInput> by lazy { planSegments.flatMap { it.inputs } }

	val score: Int get() = frames + unrestored * UNRESTORED_FRAMES

	fun beats(other: Solution): Boolean = score < other.score || score == other.score && collisionEvents < other.collisionEvents

	companion object {
		private const val UNRESTORED_FRAMES = 40

		private fun unrestoredOf(anchor: ValueAnchor, tail: List<SimulatedTrajectoryFrame>): Int {
			val original = HashMap<Long, Boolean>()
			val last = HashMap<Long, Boolean>()
			for (edit in anchor.edits) {
				val toggle = edit.physics.toggle ?: continue
				val key = BlockPos.asLong(edit.x, if (toggle.pairedY < 0) edit.y + toggle.pairedY else edit.y, edit.z)
				original.putIfAbsent(key, !toggle.open)
				last[key] = toggle.open
			}
			for (frame in tail) {
				val use = frame.input.use ?: continue
				val key = UNIT_OFFSETS.map { BlockPos.asLong(use.pos.x, use.pos.y + it, use.pos.z) }.firstOrNull { it in original }
					?: BlockPos.asLong(use.pos.x, use.pos.y, use.pos.z).also { original[it] = !use.open }
				last[key] = use.open
			}
			return original.count { (key, was) -> last[key] != was }
		}

		private val UNIT_OFFSETS = intArrayOf(0, -1, 1)

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
