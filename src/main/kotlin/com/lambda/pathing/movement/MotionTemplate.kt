package com.lambda.pathing.movement

import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.launch.BounceArcProbe
import com.lambda.pathing.launch.JumpArcProbe
import kotlin.math.abs

class MotionTemplate internal constructor(
    val id: MotionTemplateId,
    val dx: Int,
    val dy: Int,
    val dz: Int,
    val movement: MovementId,
    val lowerBoundTicks: Double,
    private val conditions: List<CellCondition>,
    private val arc: ArcSpec? = null,
    private val strideCost: Double? = null,
) {

    data class ArcSpec(

        val dx: Int,
        val dz: Int,
        val rise: Int,
        val modes: List<LaunchMode> = LaunchMode.entries,

        val bounceDrop: Int? = null,

        /**
         * Dynamic admission on the REAL rise (stance rise corrected by launch and landing
         * surface offsets), judged where the surfaces are known: a "rise 1" onto a bottom
         * trapdoor is a 0.19 ascent, onto a full block a true block of height with a
         * shorter reach. Null admits everything. See docs/decisions/movement-tuning.md.
         */
        val riseAdmission: ((Double) -> Boolean)? = null,
    )

    init {
        require(dx != 0 || dy != 0 || dz != 0) { "Motion template cannot be stationary" }
        require(lowerBoundTicks.isFinite() && lowerBoundTicks > 0.0) {
            "Template lower bound must be finite and positive: $lowerBoundTicks"
        }
    }

    fun target(origin: Stance): Stance = origin.offset(dx, dy, dz)

    internal val flightless: Boolean get() = arc == null

    internal val minimumTicks: Double = minOf(lowerBoundTicks, strideCost ?: lowerBoundTicks)

    private fun bounceEdge(
        view: CoarseVoxelView,
        origin: Stance,
        spec: ArcSpec,
        drop: Int,
        launchOffset: Double,
    ): CoarseEdge? {
        val probed = BounceArcProbe.probe(
            view, origin, spec.dx, spec.dz, drop, spec.rise,
            launchHeight = origin.y + launchOffset,
        ) ?: return null

        return CoarseEdge(
            id = CoarseEdgeId(id, origin),
            from = origin,
            to = target(origin),
            movement = movement,
            lowerBoundTicks = lowerBoundTicks,
            readSet = LazyReadSet {
                buildSet {
                    readOffsets.forEach { add(VoxelPos(origin.x + it.x, origin.y + it.y, origin.z + it.z)) }
                    addAll(probed.reads)
                }
            },
            bounce = probed.solution,
        )
    }

    private fun surfaceOffset(view: CoarseVoxelView, stance: Stance): Double =
        view.surfaceOffset(stance.x, stance.y - 1, stance.z)

    private fun realRise(view: CoarseVoxelView, origin: Stance): Double =
        dy + surfaceOffset(view, target(origin)) - surfaceOffset(view, origin)

    internal fun matches(view: CoarseVoxelView, origin: Stance): Boolean =
        conditions.all { it.matches(view, origin.x, origin.y, origin.z) }

    internal fun edge(view: CoarseVoxelView, origin: Stance): CoarseEdge? {
        if (!matches(view, origin)) return null
        val probed = arc?.let { spec ->

            val launchOffset = surfaceOffset(view, origin)
            val landingOffset = surfaceOffset(view, target(origin))
            spec.bounceDrop?.let { drop ->
                return bounceEdge(view, origin, spec, drop, launchOffset)
            }
            val riseHeight = spec.rise + landingOffset - launchOffset
            spec.riseAdmission?.let { admits -> if (!admits(riseHeight)) return null }
            JumpArcProbe.probe(
                view, origin, spec.dx, spec.dz, spec.rise, modes = spec.modes,
                riseHeight = riseHeight,
                launchHeight = origin.y + launchOffset,
            ) ?: return null
        }

        val ticks = if (strideCost != null && realRise(view, origin) <= CoarseMoveRates.FREE_STEP_RISE) {
            strideCost
        } else {
            lowerBoundTicks
        }

        return CoarseEdge(
            id = CoarseEdgeId(id, origin),
            from = origin,
            to = target(origin),
            movement = movement,
            lowerBoundTicks = ticks,
            readSet = LazyReadSet {
                buildSet {
                    readOffsets.forEach { add(VoxelPos(origin.x + it.x, origin.y + it.y, origin.z + it.z)) }
                    probed?.let { addAll(it.reads) }
                }
            },
            launch = probed?.solution,
        )
    }

    internal val readOffsets: List<VoxelPos> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildList {
            addAll(ORIGIN_STANCE_READS)
            conditions.forEach { addAll(it.reads()) }

            arc?.let { spec ->

                val steps = maxOf(abs(spec.dx), abs(spec.dz))
                for (step in 0..steps) {
                    val alongX = if (steps == 0) 0 else Math.round(spec.dx.toDouble() * step / steps).toInt()
                    val alongZ = if (steps == 0) 0 else Math.round(spec.dz.toDouble() * step / steps).toInt()
                    val floorReach = minOf(spec.rise, -(spec.bounceDrop ?: 0), 0) - 2
                    for (y in floorReach..ARC_READ_CEILING) {
                        for (ox in -1..1) {
                            for (oz in -1..1) {
                                add(VoxelPos(alongX + ox, y, alongZ + oz))
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        val ORIGIN_STANCE_READS = listOf(
            VoxelPos(0, -1, 0),
            VoxelPos(0, 0, 0),
            VoxelPos(0, 1, 0),
        )

        const val ARC_READ_CEILING = 4
    }
}
