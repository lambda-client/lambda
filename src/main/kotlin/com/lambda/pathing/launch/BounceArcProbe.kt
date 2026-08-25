package com.lambda.pathing.launch

import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.core.VoxelPos
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos
import kotlin.math.floor
import kotlin.math.hypot

object BounceArcProbe {
    class Reachable(
        val solution: BounceSolution,
        packedReads: LongOpenHashSet,
    ) {

        val reads: Set<VoxelPos> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            JumpArcProbe.unpackReads(packedReads)
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
        val length = hypot(dx.toDouble(), dz.toDouble())
        if (length <= 0.0) return null

        val reads = LongOpenHashSet()
        val cache = JumpArcProbe.SweepCellCache()
        for (sprint in SPRINT_ORDER) {
            for (holdForward in HOLD_ORDER) {
                val solution = BounceSolver.solve(
                    horizontalDistance = length,
                    drop = drop,
                    rise = rise,
                    profile = profile,
                    sprint = sprint,
                    holdForward = holdForward,
                ) ?: continue

                if (!bouncyAtContact(view, from, dx, dz, length, drop, solution, reads)) continue
                val clearance = JumpArcProbe.sweepClearance(
                    view, from, dx, dz, solution.arc,
                    launchOffset = solution.launchOffset,
                    launchHeight = launchHeight,
                    reads = reads,
                    cache = cache,
                ) ?: continue
                if (clearance < 0.0) continue

                return Reachable(solution, reads)
            }
        }
        return null
    }

    private fun bouncyAtContact(
        view: CoarseVoxelView,
        from: Stance,
        dx: Int,
        dz: Int,
        length: Double,
        drop: Int,
        solution: BounceSolution,
        reads: LongOpenHashSet,
    ): Boolean {
        val unitX = dx / length
        val unitZ = dz / length
        val reach = solution.launchOffset + solution.contactDistance
        val contactX = from.x + 0.5 + unitX * reach
        val contactZ = from.z + 0.5 + unitZ * reach

        val padY = from.y - drop - 1

        for (offsetX in -CONTACT_SLACK..CONTACT_SLACK) {
            for (offsetZ in -CONTACT_SLACK..CONTACT_SLACK) {
                val x = floor(contactX).toInt() + offsetX
                val z = floor(contactZ).toInt() + offsetZ
                reads.add(BlockPos.asLong(x, padY, z))
                if (view.voxel(x, padY, z).bouncy) return true
            }
        }
        return false
    }

    private val SPRINT_ORDER = listOf(true, false)

    private val HOLD_ORDER = listOf(true, false)

    private const val CONTACT_SLACK = 1
}
