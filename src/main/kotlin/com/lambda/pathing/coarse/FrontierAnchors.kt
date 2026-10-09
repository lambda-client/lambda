package com.lambda.pathing.coarse

import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import kotlin.math.hypot
import java.util.PriorityQueue

internal class FrontierSweep(
	private val view: CoarseVoxelView,
	private val moves: SimpleMoveLibrary,
	private val goal: Stance,
	private val edges: CoarseEdgeCache,
	private val maxNodes: Int = FrontierAnchors.SWEEP_MAX_NODES,
	private val capturable: (Int, Int) -> Boolean = FrontierAnchors.NOTHING_CAPTURABLE,
	private val onCaptureLag: (Int, Int, Int) -> Unit = { _, _, _ -> },
) {
	private val visited = HashSet<Stance>()
	private val incomplete = HashSet<Stance>()
	var dirty = false

	fun anchorsFrom(from: Stance, cancelled: () -> Boolean = { false }): Map<Stance, Double> {
		if (from == goal || FrontierAnchors.refusesOptimism(view, moves, goal)) return emptyMap()
		val seeds: Collection<Stance> = if (!dirty && from in visited) ArrayList(incomplete) else {
			if (!moves.isStance(view, from)) return emptyMap()
			visited.clear()
			incomplete.clear()
			dirty = false
			listOf(from)
		}

		val anchors = HashMap<Stance, Double>()
		val queue = PriorityQueue<Stance>(compareBy { moves.heuristic(it, goal) })
		for (seed in seeds) {
			visited += seed
			queue += seed
		}

		val slackTicks = moves.heuristic(goal, goal.offset(SWEEP_SLACK_BLOCKS, 0, 0))
		var bestAnchorTicks = Double.POSITIVE_INFINITY
		var expanded = 0
		while (queue.isNotEmpty() && expanded < maxNodes && anchors.size < SWEEP_MAX_ANCHORS) {
			if (cancelled()) break
			val node = queue.poll()
			if (moves.heuristic(node, goal) > bestAnchorTicks + slackTicks) break
			expanded++
			if (node != goal) {
				var frontier = false
				view.forEachUnknownNeighbor(node) { x, y, z ->
					if (capturable(x shr 4, z shr 4)) onCaptureLag(x, y, z)
					else frontier = true
				}
				if (frontier) {
					anchors[node] = optimisticCost(node)
					bestAnchorTicks = minOf(bestAnchorTicks, moves.heuristic(node, goal))
				}
			}
			edges.forEachEdgeFrom(PackedStance.pack(node, SpeedClass.STOPPED)) { template, _, _ ->
				val to = node.offset(template.dx, template.dy, template.dz)
				if (visited.add(to)) queue += to
			}
			if (edges.isComplete(node)) incomplete -= node else incomplete += node
		}
		return anchors
	}

	private fun optimisticCost(anchor: Stance): Double {
		val dx = (goal.x - anchor.x).toDouble()
		val dz = (goal.z - anchor.z).toDouble()
		val realistic = hypot(dx, dz) * moves.sustainedTicksPerBlock
		return maxOf(moves.heuristic(anchor, goal), realistic)
	}

	private companion object {
		const val SWEEP_SLACK_BLOCKS = 32

		const val SWEEP_MAX_ANCHORS = 256
	}
}

object FrontierAnchors {

	val NOTHING_CAPTURABLE: (Int, Int) -> Boolean = { _, _ -> false }

	fun bordersUnknown(view: CoarseVoxelView, stance: Stance): Boolean {
		view.forEachUnknownNeighbor(stance) { _, _, _ -> return true }
		return false
	}

	fun goalResolved(view: CoarseVoxelView, goal: Stance): Boolean =
		view.isKnown(goal.x, goal.y - 1, goal.z) &&
				view.isKnown(goal.x, goal.y, goal.z) &&
				view.isKnown(goal.x, goal.y + 1, goal.z)

	fun refusesOptimism(view: CoarseVoxelView, moves: SimpleMoveLibrary, goal: Stance): Boolean =
		goalResolved(view, goal) && !moves.isStance(view, goal)

	internal const val SWEEP_MAX_NODES = 40_000
}

private inline fun CoarseVoxelView.forEachUnknownNeighbor(stance: Stance, action: (Int, Int, Int) -> Unit) {
	if (!isKnown(stance.x + 1, stance.y, stance.z)) action(stance.x + 1, stance.y, stance.z)
	if (!isKnown(stance.x - 1, stance.y, stance.z)) action(stance.x - 1, stance.y, stance.z)
	if (!isKnown(stance.x, stance.y, stance.z + 1)) action(stance.x, stance.y, stance.z + 1)
	if (!isKnown(stance.x, stance.y, stance.z - 1)) action(stance.x, stance.y, stance.z - 1)
}
