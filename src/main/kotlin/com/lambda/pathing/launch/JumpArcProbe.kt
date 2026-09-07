package com.lambda.pathing.launch

import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.CollisionClass
import com.lambda.pathing.core.VoxelPos
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.shape.VoxelShape
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

object JumpArcProbe {
    /**
     * [reads] is derived on demand by re-running the sweep with recording enabled: only
     * route-plan dependency collection asks, for a handful of edges, and eager recording
     * on every probe was a measurable slice of graph construction.
     * See docs/decisions/launch-solver.md (lazy sweep recording).
     */
    class Reachable(
        val solution: LaunchSolution,
        readsSupplier: () -> LongOpenHashSet,
    ) {

        val reads: Set<VoxelPos> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            unpackReads(readsSupplier())
        }
    }

    internal fun unpackReads(packed: LongOpenHashSet): Set<VoxelPos> {
        val result = HashSet<VoxelPos>(packed.size * 2)
        val iterator = packed.iterator()
        while (iterator.hasNext()) {
            val key = iterator.nextLong()
            result += VoxelPos(BlockPos.unpackLongX(key), BlockPos.unpackLongY(key), BlockPos.unpackLongZ(key))
        }
        return result
    }

    private data class SolveKey(
        val dx: Int,
        val dz: Int,
        val riseBits: Long,
        val modes: List<LaunchMode>,
        val profile: BallisticProfile,
    )

    private val solutionCache = ConcurrentHashMap<SolveKey, List<LaunchSolution>>()

    private fun solutions(
        from: Stance,
        to: Stance,
        dx: Int,
        dz: Int,
        profile: BallisticProfile,
        modes: List<LaunchMode>,
        riseHeight: Double,
    ): List<LaunchSolution> {
        if (solutionCache.size > SOLUTION_CACHE_LIMIT) solutionCache.clear()
        return solutionCache.computeIfAbsent(
            SolveKey(dx, dz, riseHeight.toRawBits(), modes, profile)
        ) { LaunchSolver.solve(from, to, profile, modes, rise = riseHeight) }
    }

    internal class SweepCellCache {
        val classes: Long2ByteOpenHashMap = Long2ByteOpenHashMap().apply { defaultReturnValue(UNVISITED) }
        var shapes: Long2ObjectOpenHashMap<VoxelShape>? = null

        companion object {
            const val UNVISITED: Byte = -1
        }
    }

    private class SweepPlan(
        val cells: LongArray,
        val minGaps: DoubleArray,
        val intersectsFull: BooleanArray,
    )

    private val planCache = ConcurrentHashMap<ArcSample, SweepPlan>()

    /** Test hook: determinism experiments need runs that share no memoized state. */
    internal fun clearCachesForTest() {
        solutionCache.clear()
        planCache.clear()
    }

    fun probe(
        view: CoarseVoxelView,
        from: Stance,
        dx: Int,
        dz: Int,
        rise: Int,
        profile: BallisticProfile = BallisticProfile.VANILLA,
        modes: List<LaunchMode> = LaunchMode.entries,

        riseHeight: Double = (rise).toDouble(),
        launchHeight: Double = from.y.toDouble(),
    ): Reachable? {
        val solution =
            solve(view, from, dx, dz, rise, profile, modes, riseHeight, launchHeight, reads = null)
                ?: return null
        return Reachable(solution) {
            // The recording pass replays the exact sweep sequence -- failed sweeps
            // included, since a cell that refused one arc is a dependency of the
            // edge's cost like any other.
            LongOpenHashSet().also {
                solve(view, from, dx, dz, rise, profile, modes, riseHeight, launchHeight, reads = it)
            }
        }
    }

    /** Whether a failed sweep was stopped by a PARTIAL shape -- the dodgeable class. */
    internal class SweepFailure {
        var partial = false
    }

    private fun solve(
        view: CoarseVoxelView,
        from: Stance,
        dx: Int,
        dz: Int,
        rise: Int,
        profile: BallisticProfile,
        modes: List<LaunchMode>,
        riseHeight: Double,
        launchHeight: Double,
        reads: LongOpenHashSet?,
    ): LaunchSolution? {
        val to = from.offset(dx, rise, dz)
        val cache = lazy(LazyThreadSafetyMode.NONE) { SweepCellCache() }
        val planar = launchHeight == from.y.toDouble()

        for (solution in solutions(from, to, dx, dz, profile, modes, riseHeight)) {
            val failure = SweepFailure()
            val clearance = if (planar) {
                sweepByPlan(view, from, dx, dz, solution, reads, cache, failure)
            } else {
                sweepClearance(
                    view, from, dx, dz, solution.arc, solution.launchOffset, launchHeight, reads,
                    cache.value, failure = failure,
                )
            }
            if (clearance != null) return solution.withClearance(clearance)

            // The centre line is blocked by a PARTIAL shape -- a pane, a fence post:
            // the dodgeable class. The solver already knows the lateral band of
            // parallel lines that still take off and land on the pads
            // ([LaunchSolution.lateralSlack]); sweep those before giving up. A
            // full-cube wall never triggers this (half a block of sideways shift does
            // not clear it), which keeps blocked terrain exactly as cheap as before.
            if (!failure.partial) continue
            val slack = solution.lateralSlack
            if (slack < MIN_DODGE_SLACK) continue
            for (fraction in DODGE_FRACTIONS) {
                val offset = fraction * slack
                val dodged = sweepClearance(
                    view, from, dx, dz, solution.arc, solution.launchOffset, launchHeight, reads,
                    cache.value, lateralOffset = offset, halfWidth = DODGE_HALF_WIDTH,
                ) ?: continue
                return solution.copy(lateralOffset = offset).withClearance(dodged)
            }
        }
        return null
    }

    private fun sweepByPlan(
        view: CoarseVoxelView,
        from: Stance,
        dx: Int,
        dz: Int,
        solution: LaunchSolution,
        reads: LongOpenHashSet?,
        cache: Lazy<SweepCellCache>,
        failure: SweepFailure? = null,
    ): Double? {
        if (planCache.size > PLAN_CACHE_LIMIT) planCache.clear()
        val plan = planCache.computeIfAbsent(solution.arc) {
            buildPlan(dx, dz, solution.arc, solution.launchOffset)
        }

        var clearance = CLEARANCE_CAP
        for (index in plan.cells.indices) {
            val offset = plan.cells[index]
            val x = from.x + BlockPos.unpackLongX(offset)
            val y = from.y + BlockPos.unpackLongY(offset)
            val z = from.z + BlockPos.unpackLongZ(offset)
            reads?.add(BlockPos.asLong(x, y, z))

            when (view.collisionClass(x, y, z)) {
                CollisionClass.EMPTY -> {}

                CollisionClass.UNKNOWN -> return null

                CollisionClass.FULL -> {
                    if (plan.intersectsFull[index]) return null
                    clearance = minOf(clearance, plan.minGaps[index])
                }

                CollisionClass.PARTIAL ->
                    return sweepClearance(
                        view, from, dx, dz, solution.arc, solution.launchOffset,
                        from.y.toDouble(), reads, cache.value, failure = failure,
                    )
            }
        }
        return clearance
    }

    private fun buildPlan(dx: Int, dz: Int, arc: ArcSample, launchOffset: Double): SweepPlan {
        val length = hypot(dx.toDouble(), dz.toDouble())
        val unitX = dx / length
        val unitZ = dz / length
        val launchX = 0.5 + unitX * launchOffset
        val launchZ = 0.5 + unitZ * launchOffset

        val heights = arc.heights
        val distances = arc.distances

        val order = it.unimi.dsi.fastutil.longs.LongArrayList()
        val seen = LongOpenHashSet()
        val gaps = it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap()
        gaps.defaultReturnValue(CLEARANCE_CAP)
        val intersecting = LongOpenHashSet()

        var previous = coreBox(launchX, 0.0, launchZ)
        for (index in heights.indices) {
            val along = distances[index]
            val box = coreBox(
                launchX + unitX * along,
                heights[index],
                launchZ + unitZ * along,
            )
            val swept = previous.union(box)
            previous = box

            val margin = swept.expand(CLEARANCE_CAP)
            for (y in MathHelper.floor(margin.minY)..MathHelper.floor(margin.maxY)) {
                for (z in MathHelper.floor(margin.minZ)..MathHelper.floor(margin.maxZ)) {
                    for (x in MathHelper.floor(margin.minX)..MathHelper.floor(margin.maxX)) {
                        val key = BlockPos.asLong(x, y, z)
                        if (seen.add(key)) order.add(key)

                        if (swept.intersects(
                                x.toDouble(), y.toDouble(), z.toDouble(),
                                x + 1.0, y + 1.0, z + 1.0,
                            )
                        ) {
                            intersecting.add(key)
                        } else if (y + 1.0 > swept.minY + FLOOR_CONTACT_EPSILON) {
                            val gap = gapToCell(swept, x, y, z)
                            if (gap < gaps.get(key)) gaps.put(key, gap)
                        }
                    }
                }
            }
        }

        val cells = order.toLongArray()
        val minGaps = DoubleArray(cells.size)
        val intersectsFull = BooleanArray(cells.size)
        for (index in cells.indices) {
            minGaps[index] = gaps.get(cells[index])
            intersectsFull[index] = cells[index] in intersecting
        }
        return SweepPlan(cells, minGaps, intersectsFull)
    }

    internal fun sweepClearance(
        view: CoarseVoxelView,
        from: Stance,
        dx: Int,
        dz: Int,
        arc: ArcSample,
        launchOffset: Double,
        launchHeight: Double,
        reads: LongOpenHashSet?,
        cache: SweepCellCache,

        /** Sideways shift of the whole flight line; see [LaunchSolution.lateralOffset]. */
        lateralOffset: Double = 0.0,
        failure: SweepFailure? = null,

        /**
         * Swept body half-width: the forgiving core on the centre line (the rollout
         * certifies reality), the full body on a DODGE line, whose corridor is known
         * tight. See docs/decisions/launch-solver.md (dodge lines).
         */
        halfWidth: Double = CORE_HALF_WIDTH,
    ): Double? {
        val length = hypot(dx.toDouble(), dz.toDouble())
        if (length <= 0.0) return null
        val unitX = dx / length
        val unitZ = dz / length
        val launchX = from.x + 0.5 + unitX * launchOffset - unitZ * lateralOffset
        val launchZ = from.z + 0.5 + unitZ * launchOffset + unitX * lateralOffset

        val heights = arc.heights
        val distances = arc.distances

        var clearance = CLEARANCE_CAP
        var previous = coreBox(launchX, launchHeight, launchZ, halfWidth)
        for (index in heights.indices) {
            val along = distances[index]
            val box = coreBox(
                launchX + unitX * along,
                launchHeight + heights[index],
                launchZ + unitZ * along,
                halfWidth,
            )
            val swept = previous.union(box)
            previous = box

            val margin = swept.expand(CLEARANCE_CAP)
            for (y in MathHelper.floor(margin.minY)..MathHelper.floor(margin.maxY)) {
                for (z in MathHelper.floor(margin.minZ)..MathHelper.floor(margin.maxZ)) {
                    for (x in MathHelper.floor(margin.minX)..MathHelper.floor(margin.maxX)) {
                        val key = BlockPos.asLong(x, y, z)
                        reads?.add(key)

                        val known = cache.classes.get(key)
                        val cellClass = if (known == SweepCellCache.UNVISITED) {
                            view.collisionClass(x, y, z).also { cache.classes.put(key, it.ordinal.toByte()) }
                        } else {
                            COLLISION_CLASSES[known.toInt()]
                        }
                        when (cellClass) {
                            CollisionClass.EMPTY -> {}

                            CollisionClass.UNKNOWN -> return null

                            CollisionClass.FULL -> {
                                if (swept.intersects(
                                        x.toDouble(), y.toDouble(), z.toDouble(),
                                        x + 1.0, y + 1.0, z + 1.0,
                                    )
                                ) return null
                                if (y + 1.0 > swept.minY + FLOOR_CONTACT_EPSILON) {
                                    clearance = minOf(clearance, gapToCell(swept, x, y, z))
                                }
                            }

                            CollisionClass.PARTIAL -> {
                                val shapes = cache.shapes
                                    ?: Long2ObjectOpenHashMap<VoxelShape>().also { cache.shapes = it }
                                val shape = shapes.get(key)
                                    ?: view.collisionShape(x, y, z)?.also { shapes.put(key, it) }
                                    ?: return null
                                for (bounds in shape.boundingBoxes) {
                                    val obstacle = bounds.offset(x.toDouble(), y.toDouble(), z.toDouble())
                                    if (obstacle.intersects(swept)) {
                                        failure?.partial = true
                                        return null
                                    }

                                    if (obstacle.maxY <= swept.minY + FLOOR_CONTACT_EPSILON) continue
                                    clearance = minOf(clearance, gap(swept, obstacle))
                                }
                            }
                        }
                    }
                }
            }
        }
        return clearance
    }

    internal fun coreBox(x: Double, y: Double, z: Double, halfWidth: Double = CORE_HALF_WIDTH) = Box(
        x - halfWidth, y, z - halfWidth,
        x + halfWidth, y + BODY_HEIGHT, z + halfWidth,
    )

    internal fun gap(a: Box, b: Box): Double {
        val dx = max(max(b.minX - a.maxX, a.minX - b.maxX), 0.0)
        val dy = max(max(b.minY - a.maxY, a.minY - b.maxY), 0.0)
        val dz = max(max(b.minZ - a.maxZ, a.minZ - b.maxZ), 0.0)
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun gapToCell(a: Box, x: Int, y: Int, z: Int): Double {
        val dx = max(max(x - a.maxX, a.minX - (x + 1.0)), 0.0)
        val dy = max(max(y - a.maxY, a.minY - (y + 1.0)), 0.0)
        val dz = max(max(z - a.maxZ, a.minZ - (z + 1.0)), 0.0)
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    internal const val CORE_HALF_WIDTH = 0.2
    private const val BODY_HEIGHT = Kinematics.BODY_HEIGHT
    private const val CLEARANCE_CAP = 0.5
    private const val FLOOR_CONTACT_EPSILON = 1.0E-7

    /** Below this much lateral slack there is no room to dodge anything. */
    private const val MIN_DODGE_SLACK = 0.05

    /** Dodge lines sweep the real body, not the forgiving core; see [sweepClearance]. */
    private const val DODGE_HALF_WIDTH = Kinematics.BODY_HALF_WIDTH

    /** Fractions of the lateral slack tried when the centre line hits a PARTIAL shape. */
    private val DODGE_FRACTIONS = doubleArrayOf(0.5, -0.5, 1.0, -1.0)

    private const val SOLUTION_CACHE_LIMIT = 100_000
    private const val PLAN_CACHE_LIMIT = 100_000

    private val COLLISION_CLASSES = CollisionClass.entries.toTypedArray()
}
