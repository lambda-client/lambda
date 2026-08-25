package com.lambda.pathing.coarse

import com.lambda.pathing.movement.MovementCatalog
import com.lambda.pathing.movement.Movement
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.movement.CoarseEdge
import com.lambda.pathing.movement.CoarseMoveCosts
import com.lambda.pathing.movement.MotionTemplate
import com.lambda.pathing.movement.SimpleMoveOptions
import kotlin.math.abs
import kotlin.math.hypot

class SimpleMoveLibrary private constructor(
    val catalog: MovementCatalog,
    val templates: List<MotionTemplate>,
    private val readOffsets: Set<VoxelPos>,
    val heuristicCaps: HeuristicCaps,

    val sustainedTicksPerBlock: Double,
) {
    private val minReadX = readOffsets.minOf(VoxelPos::x)
    private val maxReadX = readOffsets.maxOf(VoxelPos::x)
    private val minReadZ = readOffsets.minOf(VoxelPos::z)
    private val maxReadZ = readOffsets.maxOf(VoxelPos::z)

    data class HeuristicCaps(

        val axisTicksPerBlock: Double,

        val diagonalTicksPerPair: Double,
        val ascentTicksPerBlock: Double,
        val descentTicksPerBlock: Double,

        val straightTicksPerBlock: Double,
    )

    fun isStance(view: CoarseVoxelView, stance: Stance): Boolean {
        if (stance.y !in view.simulableStanceY) return false
        val support = view.voxel(stance.x, stance.y - 1, stance.z)
        val stands = view.standingSurface(stance.x, stance.y - 1, stance.z) != null &&
            !support.intrudesAbove &&
            view.voxel(stance.x, stance.y, stance.z).centerPassable &&
            view.voxel(stance.x, stance.y + 1, stance.z).centerPassable
        return stands || catalog.movements.any { it.occupies(view, stance) }
    }

    fun edgesFrom(view: CoarseVoxelView, origin: Stance): List<CoarseEdge> {
        if (!isStance(view, origin)) return emptyList()
        return templates.mapNotNull { it.edge(view, origin) }
    }

    fun edgesTo(view: CoarseVoxelView, target: Stance): List<CoarseEdge> {
        if (!isStance(view, target)) return emptyList()
        return templates.mapNotNull { template ->
            val origin = target.offset(-template.dx, -template.dy, -template.dz)
            if (isStance(view, origin)) template.edge(view, origin) else null
        }
    }

    fun successorCosts(view: CoarseVoxelView, origin: Stance): Map<Stance, Double> =
        edgesFrom(view, origin).minimumCostsBy { it.to }

    fun predecessorCosts(view: CoarseVoxelView, target: Stance): Map<Stance, Double> =
        edgesTo(view, target).minimumCostsBy { it.from }

    fun heuristic(from: Stance, to: Stance): Double {
        val dx = abs(to.x - from.x)
        val dz = abs(to.z - from.z)
        val paired = minOf(dx, dz)
        val straight = maxOf(dx, dz) - paired

        val octile = paired * heuristicCaps.diagonalTicksPerPair.finiteOrZero() +
            straight * heuristicCaps.axisTicksPerBlock.finiteOrZero()
        val straightLine = hypot(dx.toDouble(), dz.toDouble()) *
            heuristicCaps.straightTicksPerBlock.finiteOrZero()
        val horizontal = maxOf(octile, straightLine)
        val dy = to.y - from.y
        val vertical = when {
            dy > 0 -> dy * heuristicCaps.ascentTicksPerBlock.finiteOrZero()
            dy < 0 -> -dy * heuristicCaps.descentTicksPerBlock.finiteOrZero()
            else -> 0.0
        }
        return maxOf(horizontal, vertical)
    }

    fun affectedOrigins(changed: VoxelPos): Set<Stance> = buildSet(readOffsets.size) {
        for ((x, y, z) in readOffsets) {
            add(Stance(changed.x - x, changed.y - y, changed.z - z))
        }
    }

    fun affectedOrigins(chunk: PathingChunk, candidates: Iterable<Stance>): Set<Stance> {
        val chunkMinX = chunk.x shl 4
        val chunkMaxX = chunkMinX + 15
        val chunkMinZ = chunk.z shl 4
        val chunkMaxZ = chunkMinZ + 15
        val originX = (chunkMinX - maxReadX)..(chunkMaxX - minReadX)
        val originZ = (chunkMinZ - maxReadZ)..(chunkMaxZ - minReadZ)
        return candidates.filterTo(HashSet()) { it.x in originX && it.z in originZ }
    }

    private fun List<CoarseEdge>.minimumCostsBy(node: (CoarseEdge) -> Stance): Map<Stance, Double> {
        val result = HashMap<Stance, Double>(size)
        for (edge in this) result.merge(node(edge), edge.lowerBoundTicks, ::minOf)
        return result
    }

    private fun Double.finiteOrZero() = if (isFinite()) this else 0.0

    companion object {

        fun build(
            costs: CoarseMoveCosts,
            options: SimpleMoveOptions = SimpleMoveOptions(),
            ballistics: BallisticProfile = BallisticProfile.VANILLA,
            movements: List<Movement> = MovementCatalog.REGISTERED,
        ): SimpleMoveLibrary = of(MovementCatalog.build(costs, options, ballistics, movements))

        fun of(catalog: MovementCatalog): SimpleMoveLibrary {
            val templates = catalog.templates
            val offsets = templates.flatMapTo(HashSet()) { it.readOffsets }
            return SimpleMoveLibrary(catalog, templates, offsets, deriveCaps(templates), sustainedRate(templates))
        }

        private fun sustainedRate(templates: List<MotionTemplate>): Double = templates
            .filter { it.dy == 0 && abs(it.dx) + abs(it.dz) == 1 }
            .minOfOrNull { it.lowerBoundTicks }
            ?: 1.0

        private fun deriveCaps(templates: List<MotionTemplate>): HeuristicCaps {
            var axis = Double.POSITIVE_INFINITY
            var diagonal = Double.POSITIVE_INFINITY
            var ascent = Double.POSITIVE_INFINITY
            var descent = Double.POSITIVE_INFINITY
            var straight = Double.POSITIVE_INFINITY
            for (template in templates) {
                val reach = hypot(template.dx.toDouble(), template.dz.toDouble())
                if (reach > 0.0) straight = minOf(straight, template.minimumTicks / reach)
            }
            for (template in templates) {
                val dx = abs(template.dx)
                val dz = abs(template.dz)
                when {
                    dx > 0 && dz == 0 -> axis = minOf(axis, template.minimumTicks / dx)
                    dz > 0 && dx == 0 -> axis = minOf(axis, template.minimumTicks / dz)
                    dx == dz && dx > 0 -> diagonal = minOf(diagonal, template.minimumTicks / dx)
                }
                if (template.dy > 0) ascent = minOf(ascent, template.minimumTicks / template.dy)
                if (template.dy < 0) descent = minOf(descent, template.minimumTicks / abs(template.dy))
            }

            axis = minOf(axis, diagonal)
            diagonal = minOf(diagonal, 2.0 * axis)

            for (template in templates) {
                val a = abs(template.dx)
                val b = abs(template.dz)
                if (a == 0 || b == 0 || a == b) continue
                val pairs = minOf(a, b)
                val straights = abs(a - b)
                val claimed = pairs * diagonal + straights * axis
                if (!claimed.isFinite() || claimed <= template.minimumTicks) continue

                val roomForDiagonal = (template.minimumTicks - straights * axis) / pairs
                if (roomForDiagonal >= axis) {
                    diagonal = roomForDiagonal
                } else {
                    val even = template.minimumTicks / (pairs + straights)
                    axis = even
                    diagonal = even
                }
            }

            return HeuristicCaps(axis, diagonal, ascent, descent, straight)
        }
    }
}
