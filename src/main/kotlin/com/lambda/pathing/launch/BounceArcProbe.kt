package com.lambda.pathing.launch

import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.core.VoxelPos
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

object BounceArcProbe {
    /** [reads] re-runs the probe with recording on, like [JumpArcProbe.Reachable]. */
    class Reachable(
        val solution: BounceSolution,
        readsSupplier: () -> LongOpenHashSet,
    ) {

        val reads: Set<VoxelPos> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            JumpArcProbe.unpackReads(readsSupplier())
        }
    }

    fun probe(
        view: CoarseVoxelView,
        from: Stance,
        dx: Int,
        dz: Int,
        drop: Int,
        rise: Int,
        profile: BallisticProfile = BallisticProfile.VANILLA,
        launchHeight: Double = from.y.toDouble(),
    ): Reachable? {
        val solution = solve(view, from, dx, dz, drop, rise, profile, launchHeight, reads = null)
            ?: return null
        return Reachable(solution) {
            LongOpenHashSet().also {
                solve(view, from, dx, dz, drop, rise, profile, launchHeight, reads = it)
            }
        }
    }

    private fun solve(
        view: CoarseVoxelView,
        from: Stance,
        dx: Int,
        dz: Int,
        drop: Int,
        rise: Int,
        profile: BallisticProfile,
        launchHeight: Double,
        reads: LongOpenHashSet?,
    ): BounceSolution? {
        val length = hypot(dx.toDouble(), dz.toDouble())
        if (length <= 0.0) return null

        val unitX = dx / length
        val unitZ = dz / length
        val padY = from.y - drop - 1

        // No slime under the flight ray, no bounce: everything past this line costs
        // dozens of arc solves per launch style. See docs/decisions/launch-solver.md.
        var anyBouncy = false
        var along = 1.0
        while (along < length + 0.5) {
            if (bouncyCell(view, floor(from.x + 0.5 + unitX * along).toInt(), padY,
                    floor(from.z + 0.5 + unitZ * along).toInt(), reads)
            ) {
                anyBouncy = true
                break
            }
            along += 1.0
        }
        if (!anyBouncy) return null

        // The solver flies REAL heights relative to the launch feet, not stance
        // deltas: a partial landing surface (and the launch's own) shifts the rise.
        val launchOffset = launchHeight - from.y
        reads?.add(BlockPos.asLong(from.x + dx, from.y + rise - 1, from.z + dz))
        val landingOffset = view.surfaceOffset(from.x + dx, from.y + rise - 1, from.z + dz)
        val riseHeight = rise + landingOffset - launchOffset

        // The ceiling over the LAUNCH ASCENT only: the solver clamps the ascent there
        // with vertical speed zeroed (vanilla's rising head collision), so a grazing
        // jump still solves. Not the whole ray -- a lower ceiling over the descending
        // landing would bury the launch; one the arc genuinely hits still fails the sweep.
        // See docs/decisions/launch-solver.md (headroom).
        var ceilingBottom = Double.POSITIVE_INFINITY
        run {
            val feetCell = floor(launchHeight).toInt()
            var along = 0.0
            while (along <= minOf(length, LAUNCH_ASCENT_REACH)) {
                val x = floor(from.x + 0.5 + unitX * along).toInt()
                val z = floor(from.z + 0.5 + unitZ * along).toInt()
                for (y in feetCell + 2..feetCell + 4) {
                    reads?.add(BlockPos.asLong(x, y, z))
                    val v = view.voxel(x, y, z)
                    if (!v.fullyPassable) {
                        val bottom = y + (view.collisionShape(x, y, z)?.getMin(net.minecraft.util.math.Direction.Axis.Y) ?: 0.0)
                        if (bottom < ceilingBottom) ceilingBottom = bottom
                        break
                    }
                }
                along += 1.0
            }
        }
        val headroom = if (ceilingBottom.isFinite()) ceilingBottom - BODY_HEIGHT - launchHeight else Double.POSITIVE_INFINITY

        // How far along the ray the body can stand at launch: the support shape's
        // own extent plus the body's half-width. A full block reaches 0.8, a fence
        // post 0.425 -- solving (and creeping) to a full-block lip on a post is a
        // walk straight off it into the gap.
        val launchReach = launchReach(view, from, launchHeight, unitX, unitZ, reads)

        val cache = JumpArcProbe.SweepCellCache()

        // Judges a contact reach: infinite off the pad, otherwise the distance from
        // the pad cell's centre -- on a one-block pad the solver aims the trough
        // through the middle, leaving execution spread room on both sides.
        fun contactPenalty(reach: Double): Double {
            val px = from.x + 0.5 + unitX * reach
            val pz = from.z + 0.5 + unitZ * reach
            val x = floor(px).toInt()
            val z = floor(pz).toInt()
            if (!bouncyCell(view, x, padY, z, reads)) return Double.POSITIVE_INFINITY
            return hypot(px - (x + 0.5), pz - (z + 0.5))
        }

        fun admissible(solution: BounceSolution): Boolean {
            if (!contactPenalty(solution.launchOffset + solution.contactDistance).isFinite()) return false
            val clearance = JumpArcProbe.sweepClearance(
                view, from, dx, dz, solution.arc,
                launchOffset = solution.launchOffset,
                launchHeight = launchHeight,
                reads = reads,
                cache = cache,
            ) ?: return false
            return clearance >= 0.0
        }

        // Standing starts first: a launch from rest has no entry speed to reproduce,
        // while a moving entry amplifies its error ~16x over the glide. Every standing
        // style is offered to the sweep (a long hold can clip the far wall where a
        // gentler one clears). See docs/decisions/launch-solver.md (standing starts).
        for ((jump, sprint) in BounceSolver.STANDING_STYLES) {
            val solution = solveRefined(view, from, dx, dz, length, drop, launchOffset, reads) { contactDepth ->
                BounceSolver.solveStanding(
                    horizontalDistance = length, drop = drop, rise = rise,
                    jump = jump, sprint = sprint, profile = profile,
                    contactDepth = contactDepth, riseHeight = riseHeight,
                    contactPenalty = ::contactPenalty,
                    headroom = headroom,
                    launchReach = launchReach,
                )
            } ?: continue
            if (!admissible(solution)) continue
            return solution
        }

        for (combo in COMBOS) {
            val solution = solveRefined(view, from, dx, dz, length, drop, launchOffset, reads) { contactDepth ->
                BounceSolver.solve(
                    horizontalDistance = length, drop = drop, rise = rise, profile = profile,
                    sprint = combo.sprint, holdForward = combo.holdForward,
                    jump = combo.jump, holdTicks = combo.holdTicks,
                    contactDepth = contactDepth, riseHeight = riseHeight,
                    headroom = headroom,
                    launchReach = launchReach,
                )
            } ?: continue
            if (!admissible(solution)) continue
            return solution
        }
        return null
    }

    /** One launch style the solver may fly: jump off the lip or walk off it, sprint, hold. */
    data class LaunchCombo(
        val jump: Boolean,
        val sprint: Boolean,
        val holdForward: Boolean,
        val holdTicks: Int = Int.MAX_VALUE,
    )

    /**
     * Solve against the pad's REAL contact surface (a carpeted pad's is 0.9375 below its
     * coarse stance). The contact CELL depends on the solved arc, so solve, look up the
     * surface there, and re-solve until the assumption holds.
     * See docs/decisions/launch-solver.md (real heights).
     */
    private inline fun solveRefined(
        view: CoarseVoxelView,
        from: Stance,
        dx: Int,
        dz: Int,
        length: Double,
        drop: Int,
        launchOffset: Double,
        reads: LongOpenHashSet?,
        solveAtDepth: (Double) -> BounceSolution?,
    ): BounceSolution? {
        var contactOffset = seedContactOffset(view, from, dx, dz, length, drop, reads)
        repeat(CONTACT_REFINEMENTS) {
            val solution = solveAtDepth(drop + launchOffset - contactOffset) ?: return null
            val observed = contactSurfaceOffset(view, from, dx, dz, length, drop, solution, reads)
            if (abs(observed - contactOffset) < CONTACT_CONVERGENCE) return solution
            contactOffset = observed
        }
        return null
    }

    /**
     * First guess for [solveRefined]: the first standable surface the flight ray crosses
     * on the pad level (the integer plane fails where only the corrected depth admits a
     * solution). The fixed-point pass corrects any cell mismatch.
     */
    private fun seedContactOffset(
        view: CoarseVoxelView,
        from: Stance,
        dx: Int,
        dz: Int,
        length: Double,
        drop: Int,
        reads: LongOpenHashSet?,
    ): Double {
        val padY = from.y - drop - 1
        var along = 1.0
        while (along < length) {
            val x = floor(from.x + 0.5 + (dx / length) * along).toInt()
            val z = floor(from.z + 0.5 + (dz / length) * along).toInt()
            reads?.add(BlockPos.asLong(x, padY, z))
            if (view.voxel(x, padY, z).standingSurface != null) return view.surfaceOffset(x, padY, z)
            along += 1.0
        }
        return 0.0
    }

    private fun contactSurfaceOffset(
        view: CoarseVoxelView,
        from: Stance,
        dx: Int,
        dz: Int,
        length: Double,
        drop: Int,
        solution: BounceSolution,
        reads: LongOpenHashSet?,
    ): Double {
        val reach = solution.launchOffset + solution.contactDistance
        val x = floor(from.x + 0.5 + (dx / length) * reach).toInt()
        val z = floor(from.z + 0.5 + (dz / length) * reach).toInt()
        val padY = from.y - drop - 1
        reads?.add(BlockPos.asLong(x, padY, z))
        return view.surfaceOffset(x, padY, z)
    }

    /**
     * Whether the single cell under a contact point bounces, directly or through thin
     * cover (the game samples the landing block 0.2 BELOW the feet,
     * MovementSimulator.LANDING_Y_OFFSET). STRICT on purpose: a one-cell slack blessed
     * contacts on the pit floor beside the slime. See docs/decisions/launch-solver.md.
     */
    private fun bouncyCell(
        view: CoarseVoxelView,
        x: Int,
        padY: Int,
        z: Int,
        reads: LongOpenHashSet?,
    ): Boolean {
        reads?.add(BlockPos.asLong(x, padY, z))
        val pad = view.voxel(x, padY, z)
        if (pad.bouncy) return true
        val surface = pad.standingSurface
        if (surface != null && surface <= THIN_COVER_SURFACE) {
            reads?.add(BlockPos.asLong(x, padY - 1, z))
            if (view.voxel(x, padY - 1, z).bouncy) return true
        }
        return false
    }

    /**
     * Combo order: jump launches first (the only launch landing less than two below the
     * lip), then walk-off; within a style longest hold, then these partial holds, then
     * released. (jump, sprint, hold < 2) is excluded: vanilla drops sprint the moment
     * forward is released. See docs/decisions/launch-solver.md (combo order).
     */
    private val PARTIAL_HOLDS = intArrayOf(20, 16, 12, 8, 5, 3, 2)

    private val COMBOS = buildList {
        for (jump in listOf(true, false)) {
            add(LaunchCombo(jump, sprint = true, holdForward = true))
            add(LaunchCombo(jump, sprint = false, holdForward = true))
            for (holdTicks in PARTIAL_HOLDS) {
                // Sprint partials keep the boost honest: holdTicks >= 2 means forward
                // is still held on the launch tick, so vanilla is sprinting when the
                // jump fires.
                add(LaunchCombo(jump, sprint = true, holdForward = true, holdTicks = holdTicks))
                add(LaunchCombo(jump, sprint = false, holdForward = true, holdTicks = holdTicks))
            }
            add(LaunchCombo(jump, sprint = false, holdForward = false))
        }
    }

    /**
     * The standable reach along the flight ray from the launch cell's centre: the support
     * shape (the cell under the stance, or the one below when the surface is an intrusion
     * such as a fence top) counting only boxes that carry the feet, plus the body's
     * half-width. Falls back to the full-block reach when the view carries no shapes.
     */
    private fun launchReach(
        view: CoarseVoxelView,
        from: Stance,
        launchHeight: Double,
        unitX: Double,
        unitZ: Double,
        reads: LongOpenHashSet?,
    ): Double {
        val direct = view.voxel(from.x, from.y - 1, from.z).standingSurface != null
        val supportY = if (direct) from.y - 1 else from.y - 2
        reads?.add(BlockPos.asLong(from.x, supportY, from.z))
        val shape = view.collisionShape(from.x, supportY, from.z)
            ?: return BounceSolver.LAUNCH_OFFSET
        val surfaceLocal = launchHeight - supportY
        var extent = Double.NEGATIVE_INFINITY
        for (box in shape.boundingBoxes) {
            if (box.maxY < surfaceLocal - SUPPORT_SURFACE_EPSILON) continue
            val alongX = maxOf((box.minX - 0.5) * unitX, (box.maxX - 0.5) * unitX)
            val alongZ = maxOf((box.minZ - 0.5) * unitZ, (box.maxZ - 0.5) * unitZ)
            extent = maxOf(extent, alongX + alongZ)
        }
        if (extent == Double.NEGATIVE_INFINITY) return BounceSolver.LAUNCH_OFFSET
        return (extent + BODY_HALF_WIDTH).coerceIn(BODY_HALF_WIDTH, BounceSolver.LAUNCH_OFFSET)
    }

    private const val BODY_HALF_WIDTH = 0.3

    private const val SUPPORT_SURFACE_EPSILON = 1.0E-4

    /** Cover no taller than the game's 0.2 landing probe still bounces off what's below. */
    private const val THIN_COVER_SURFACE = 0.2

    private const val BODY_HEIGHT = 1.8

    /** Blocks of ray the launch ascent spans before the arc is past its apex. */
    private const val LAUNCH_ASCENT_REACH = 3.5

    private const val CONTACT_REFINEMENTS = 3

    private const val CONTACT_CONVERGENCE = 1.0E-9
}
