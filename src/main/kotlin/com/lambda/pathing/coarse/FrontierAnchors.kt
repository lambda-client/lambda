package com.lambda.pathing.coarse

import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

object FrontierAnchors {

    /**
     * Chunks the client could capture into the snapshot right now. Unknown cells in
     * such a chunk are KNOWLEDGE LAG, not frontier: the right response is waiting for
     * capture, never an optimistic anchor. Anchoring on them is how a cold start walks
     * random two-block routes toward its own uncaptured surroundings, each retired by
     * the next capture batch -- the body wanders until the real graph connects. The
     * default claims nothing is capturable, which preserves the pure-view behaviour
     * for callers without a client world (tests, replays).
     */
    val NOTHING_CAPTURABLE: (Int, Int) -> Boolean = { _, _ -> false }

    /**
     * Suppressing an anchor is only half a policy. The retired optimism was also the
     * planner's knowledge PULL: an anchor placed the route terminal at the lip, and
     * route/terminal interest then demanded capture of exactly that area. Refusing
     * the anchor without demanding the knowledge instead leaves the search stuck
     * against a lagging column nothing ever captures -- measured in the field as a
     * parkour section whose wide-corridor diagonal jumps stayed refused while the
     * graph "refused to advance". [onCaptureLag] reports every capturable-unknown
     * cell that suppressed an anchor so the caller can prime DEMAND interest there.
     */
    fun probe(
        view: CoarseVoxelView,
        moves: SimpleMoveLibrary,
        from: Stance,
        goal: Stance,
        maxSteps: Int = MAX_STEPS,
        capturable: (Int, Int) -> Boolean = NOTHING_CAPTURABLE,
        onCaptureLag: (Int, Int, Int) -> Unit = { _, _, _ -> },
    ): Map<Stance, Double> {
        if (from == goal || refusesOptimism(view, moves, goal)) return emptyMap()

        val dx = (goal.x - from.x).toDouble()
        val dz = (goal.z - from.z).toDouble()
        val length = hypot(dx, dz)
        if (length < 1.0) return emptyMap()

        val anchors = HashMap<Stance, Double>()
        for (degrees in FAN_DEGREES) {
            val radians = Math.toRadians(degrees)
            val headingX = (dx * cos(radians) - dz * sin(radians)) / length
            val headingZ = (dx * sin(radians) + dz * cos(radians)) / length
            val anchor = march(view, moves, from, headingX, headingZ, maxSteps, capturable, onCaptureLag)
                ?: continue
            if (anchor == goal) continue
            val cost = optimisticCost(moves, anchor, goal)
            anchors.merge(anchor, cost, ::minOf)
        }
        return anchors
    }

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

            for (edge in moves.edgesFrom(view, node)) {
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

    private fun march(
        view: CoarseVoxelView,
        moves: SimpleMoveLibrary,
        from: Stance,
        headingX: Double,
        headingZ: Double,
        maxSteps: Int,
        capturable: (Int, Int) -> Boolean,
        onCaptureLag: (Int, Int, Int) -> Unit,
    ): Stance? {
        var anchor: Stance? = null
        var level = from.y
        var step = 1

        var stride = STRIDE
        var frontier = false
        while (step <= maxSteps) {
            val x = from.x + (headingX * step).roundToInt()
            val z = from.z + (headingZ * step).roundToInt()
            if (!view.isKnown(x, level, z)) {
                if (stride == 1) {
                    // A capturable unknown ends the march the same way -- the view
                    // cannot see past it -- but yields no anchor: it is capture lag,
                    // reported so the caller can demand exactly this knowledge.
                    frontier = !capturable(x shr 4, z shr 4)
                    if (!frontier) onCaptureLag(x, level, z)
                    break
                }
                step = maxOf(1, step - (stride - 1))
                stride = 1
                continue
            }
            surface(view, moves, x, z, level)?.let {
                level = it.y
                anchor = it
            }
            step += stride
        }

        return if (frontier) anchor?.takeIf { it != from } else null
    }

    private fun surface(
        view: CoarseVoxelView,
        moves: SimpleMoveLibrary,
        x: Int,
        z: Int,
        level: Int,
    ): Stance? {
        for (offset in 0..SURFACE_REACH) {
            val above = Stance(x, level + offset, z)
            if (view.isKnown(x, above.y, z) && moves.isStance(view, above)) return above
            if (offset == 0) continue
            val below = Stance(x, level - offset, z)
            if (view.isKnown(x, below.y, z) && moves.isStance(view, below)) return below
        }
        return null
    }

    private val FAN_DEGREES = listOf(0.0, -35.0, 35.0)

    private const val SURFACE_REACH = 4

    private const val STRIDE = 8

    private const val MAX_STEPS = 512

    private const val SWEEP_MAX_NODES = 40_000

    private const val SWEEP_MAX_ANCHORS = 256
}
