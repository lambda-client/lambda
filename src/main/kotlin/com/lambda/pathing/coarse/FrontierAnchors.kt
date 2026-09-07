package com.lambda.pathing.coarse

import com.lambda.pathing.core.Stance
import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.world.CoarseVoxelView
import kotlin.math.hypot

object FrontierAnchors {

    /**
     * Default `capturable` predicate: unknown cells in a chunk the client could capture
     * now are knowledge lag, never frontier. Claiming nothing is capturable preserves
     * pure-view behaviour for callers without a client world (tests, replays).
     * See docs/decisions/anchor-lifecycle.md.
     */
    val NOTHING_CAPTURABLE: (Int, Int) -> Boolean = { _, _ -> false }

    fun sweep(
        view: CoarseVoxelView,
        moves: SimpleMoveLibrary,
        from: Stance,
        goal: Stance,
        maxNodes: Int = SWEEP_MAX_NODES,
        maxAnchors: Int = SWEEP_MAX_ANCHORS,
        cancelled: () -> Boolean = { false },
        capturable: (Int, Int) -> Boolean = NOTHING_CAPTURABLE,
        onCaptureLag: (Int, Int, Int) -> Unit = { _, _, _ -> },

        /** Edge source; pass the session [CoarseEdgeCache] so repeated sweeps reuse probes. */
        edges: (Stance) -> List<CoarseEdge> = { moves.edgesFrom(view, it) },
    ): Map<Stance, Double> {
        if (from == goal || refusesOptimism(view, moves, goal)) return emptyMap()
        if (!moves.isStance(view, from)) return emptyMap()

        val anchors = HashMap<Stance, Double>()
        val visited = HashSet<Stance>()
        val queue = java.util.PriorityQueue<Stance>(compareBy { moves.heuristic(it, goal) })
        visited += from
        queue += from

        var expanded = 0
        while (queue.isNotEmpty() && expanded < maxNodes && anchors.size < maxAnchors) {
            if (cancelled()) break
            val node = queue.poll()
            expanded++
            if (node != goal) {
                var frontier = false
                forEachUnknownNeighbor(view, node) { x, y, z ->
                    if (capturable(x shr 4, z shr 4)) onCaptureLag(x, y, z)
                    else frontier = true
                }
                if (frontier) anchors[node] = optimisticCost(moves, node, goal)
            }

            for (edge in edges(node)) {
                if (visited.add(edge.to)) queue += edge.to
            }
        }
        return anchors
    }

    private inline fun forEachUnknownNeighbor(
        view: CoarseVoxelView,
        stance: Stance,
        action: (Int, Int, Int) -> Unit,
    ) {
        if (!view.isKnown(stance.x + 1, stance.y, stance.z)) action(stance.x + 1, stance.y, stance.z)
        if (!view.isKnown(stance.x - 1, stance.y, stance.z)) action(stance.x - 1, stance.y, stance.z)
        if (!view.isKnown(stance.x, stance.y, stance.z + 1)) action(stance.x, stance.y, stance.z + 1)
        if (!view.isKnown(stance.x, stance.y, stance.z - 1)) action(stance.x, stance.y, stance.z - 1)
    }

    fun bordersUnknown(
        view: CoarseVoxelView,
        stance: Stance,
        capturable: (Int, Int) -> Boolean = NOTHING_CAPTURABLE,
    ): Boolean =
        frontierAt(view, capturable, stance.x + 1, stance.y, stance.z) ||
            frontierAt(view, capturable, stance.x - 1, stance.y, stance.z) ||
            frontierAt(view, capturable, stance.x, stance.y, stance.z + 1) ||
            frontierAt(view, capturable, stance.x, stance.y, stance.z - 1)

    private fun frontierAt(
        view: CoarseVoxelView,
        capturable: (Int, Int) -> Boolean,
        x: Int,
        y: Int,
        z: Int,
    ): Boolean = !view.isKnown(x, y, z) && !capturable(x shr 4, z shr 4)

    fun goalResolved(view: CoarseVoxelView, goal: Stance): Boolean =
        view.isKnown(goal.x, goal.y - 1, goal.z) &&
            view.isKnown(goal.x, goal.y, goal.z) &&
            view.isKnown(goal.x, goal.y + 1, goal.z)

    fun refusesOptimism(view: CoarseVoxelView, moves: SimpleMoveLibrary, goal: Stance): Boolean =
        goalResolved(view, goal) && !moves.isStance(view, goal)

    internal fun optimisticCost(moves: SimpleMoveLibrary, anchor: Stance, goal: Stance): Double {
        val dx = (goal.x - anchor.x).toDouble()
        val dz = (goal.z - anchor.z).toDouble()
        val realistic = hypot(dx, dz) * moves.sustainedTicksPerBlock
        return maxOf(moves.heuristic(anchor, goal), realistic)
    }

    private const val SWEEP_MAX_NODES = 40_000

    private const val SWEEP_MAX_ANCHORS = 256
}
