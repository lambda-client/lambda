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

        // The solver flies REAL heights relative to the launch feet, not stance
        // deltas: a partial landing surface (and the launch's own) shifts the rise.
        val launchOffset = launchHeight - from.y
        reads?.add(BlockPos.asLong(from.x + dx, from.y + rise - 1, from.z + dz))
        val landingOffset = view.surfaceOffset(from.x + dx, from.y + rise - 1, from.z + dz)
        val riseHeight = rise + landingOffset - launchOffset

        val cache = JumpArcProbe.SweepCellCache()
        val unitX = dx / length
        val unitZ = dz / length
        val padY = from.y - drop - 1

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

        // Standing starts first: a launch from rest at the lip has no entry speed for
        // the program to reproduce, and a moving-entry bounce is a knife edge -- a
        // thirty-tick glide amplifies entry error about sixteenfold, measured
        // overflying the pad by a block off a 0.07 approach error.
        // Every standing style is offered to the sweep: a long-hold style can put
        // its contact past the pad or clip the far wall on the rebound where a
        // gentler one contacts deep in the pit and clears.
        for ((jump, sprint) in BounceSolver.STANDING_STYLES) {
            val solution = solveRefined(view, from, dx, dz, length, drop, launchOffset, reads) { contactDepth ->
                BounceSolver.solveStanding(
                    horizontalDistance = length, drop = drop, rise = rise,
                    jump = jump, sprint = sprint, profile = profile,
                    contactDepth = contactDepth, riseHeight = riseHeight,
                    contactPenalty = ::contactPenalty,
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
     * The pad's contact surface is where the body really bounces, and a carpeted pad's
     * surface is 0.9375 below its coarse contact stance -- solved against the integer
     * plane the arc contacted a block early with the wrong impact speed and never
     * certified. The contact CELL depends on the solved arc, so solve, look up the
     * surface where that arc contacts, and re-solve until the assumption holds.
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
     * The refinement needs a first guess it can solve AT: seeding with the integer
     * plane fails when only the corrected depth admits a solution (a carpeted pad
     * shifts the window by nearly a block), so the seed is the first standable
     * surface the flight ray crosses on the pad level -- pads are uniform under an
     * arc in practice, and the fixed-point pass corrects any cell mismatch.
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
     * Whether the single cell under a contact point bounces -- directly, or through
     * thin cover: the game samples the landing block 0.2 BELOW the feet
     * (MovementSimulator.LANDING_Y_OFFSET), so carpet-on-slime bounces in reality
     * and in the rollout. STRICT on purpose: this used to accept any cell within a
     * one-cell slack, and on a one-block pad that blessed arcs whose contact fell on
     * the pit floor BESIDE the slime -- the body took the fall the exemption was
     * promising away.
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
     * Jump launches first -- the human default, and the only launch that lands less
     * than two below the lip; their higher arcs fail the sweep under a tight ceiling
     * and fall through to the walk-off combos. Within a launch style, longer holds
     * first (they leave the most entry-speed headroom), then the partial holds that
     * cover the dead band between the released and held lines, then released.
     * (jump, sprint, released/short-hold) is excluded: vanilla drops sprint the
     * moment forward is released, so the boost the model assumes is unreliable.
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

    /** Cover no taller than the game's 0.2 landing probe still bounces off what's below. */
    private const val THIN_COVER_SURFACE = 0.2

    private const val CONTACT_REFINEMENTS = 3

    private const val CONTACT_CONVERGENCE = 1.0E-9
}
