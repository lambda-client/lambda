package com.lambda.pathing.launch

import com.lambda.pathing.core.Stance
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class LaunchSolution(
    val mode: LaunchMode,

    val launchOffset: Double,
    val speed: Double,

    val speedSlack: Double,

    val landingSlack: Double,

    val headroom: Double,

    val aimDistance: Double,
    val lateralSlack: Double,

    val holdForward: Boolean,

    val arc: ArcSample,
    val clearance: Double = 0.0,
) {
    val sprint: Boolean get() = mode.sprint

    val jumps: Boolean get() = mode.jumps

    val airTicks: Int get() = arc.airTicks

    val apex: Double get() = arc.apex

    val flightDistance: Double get() = aimDistance - launchOffset

    fun withClearance(clearance: Double) = copy(clearance = clearance)

    val margin: Double get() =
        landingSlack + LATERAL_MARGIN_WEIGHT * lateralSlack +
            HEADROOM_WEIGHT * (if (jumps) headroom else -headroom)

    override fun equals(other: Any?): Boolean = this === other ||
        (other is LaunchSolution && mode == other.mode &&
            launchOffset == other.launchOffset && speed == other.speed &&
            aimDistance == other.aimDistance && airTicks == other.airTicks)

    override fun hashCode(): Int {
        var result = mode.hashCode()
        result = 31 * result + launchOffset.hashCode()
        result = 31 * result + speed.hashCode()
        result = 31 * result + aimDistance.hashCode()
        result = 31 * result + airTicks
        return result
    }

    companion object {

        val BEST_FIRST: Comparator<LaunchSolution> =
            compareByDescending<LaunchSolution> { it.margin }.thenBy { it.sprint }

        private const val LATERAL_MARGIN_WEIGHT = 0.25
        private const val HEADROOM_WEIGHT = 0.1
    }
}

object LaunchSolver {

    fun solve(
        from: Stance,
        to: Stance,
        profile: BallisticProfile = BallisticProfile.VANILLA,
        modes: List<LaunchMode> = LaunchMode.entries,
        maxEntrySpeed: (LaunchMode) -> Double = { profile.momentumSpeed(it.sprint) },
        preferredEntrySpeed: (LaunchMode) -> Double = { profile.cruiseSpeed(it.sprint) },

        rise: Double = (to.y - from.y).toDouble(),
    ): List<LaunchSolution> {
        val dx = (to.x - from.x).toDouble()
        val dz = (to.z - from.z).toDouble()
        val length = hypot(dx, dz)
        if (length <= 1e-9) return emptyList()
        val unitX = dx / length
        val unitZ = dz / length

        val lateral = lateralSlack(unitX, unitZ)
        if (lateral < 0.0) return emptyList()

        return modes.filter { it.supports(rise) }.mapNotNull { mode ->
            solveMode(
                from, to, unitX, unitZ, rise, lateral, mode, profile,
                maxEntrySpeed(mode), preferredEntrySpeed(mode),
            )
        }.sortedWith(LaunchSolution.BEST_FIRST)
    }

    fun best(
        from: Stance,
        to: Stance,
        profile: BallisticProfile = BallisticProfile.VANILLA,
        modes: List<LaunchMode> = LaunchMode.entries,
        maxEntrySpeed: (LaunchMode) -> Double = { profile.momentumSpeed(it.sprint) },
        preferredEntrySpeed: (LaunchMode) -> Double = { profile.cruiseSpeed(it.sprint) },
        rise: Double = (to.y - from.y).toDouble(),
    ): LaunchSolution? =
        solve(from, to, profile, modes, maxEntrySpeed, preferredEntrySpeed, rise).firstOrNull()

    private fun solveMode(
        from: Stance,
        to: Stance,
        unitX: Double,
        unitZ: Double,
        rise: Double,
        lateral: Double,
        mode: LaunchMode,
        profile: BallisticProfile,
        maxEntrySpeed: Double,
        preferredEntrySpeed: Double,
    ): LaunchSolution? {
        if (maxEntrySpeed < 0.0) return null

        val window = landingWindow(from, to, unitX, unitZ) ?: return null

        var best: LaunchSolution? = null

        val policies = if (mode.drops) listOf(true, false) else listOf(true)

        for (holdForward in policies) {

            val base = profile.fly(mode, 0.0, rise, holdForward) ?: continue
            val unit = profile.fly(mode, 1.0, rise, holdForward) ?: continue
            val slope = unit.distance - base.distance
            if (slope <= 1e-9) continue

            for (offset in launchOffsets(mode)) {

                fun speedFor(distance: Double) = (distance - offset - base.distance) / slope

                val feasibleLow = max(speedFor(window.start), 0.0)
                val feasibleHigh = min(speedFor(window.endInclusive), maxEntrySpeed)
                if (feasibleHigh < feasibleLow) continue

                val speed = preferredSpeed(preferredEntrySpeed, feasibleLow, feasibleHigh, slope)

                val arc = profile.fly(mode, speed, rise, holdForward) ?: continue
                val candidate = LaunchSolution(
                    mode = mode,
                    launchOffset = offset,
                    speed = speed,
                    speedSlack = min(speed - feasibleLow, feasibleHigh - speed),
                    landingSlack = min(speed - feasibleLow, feasibleHigh - speed) * slope,
                    headroom = if (maxEntrySpeed > 0.0) (maxEntrySpeed - speed) / maxEntrySpeed else 0.0,
                    aimDistance = offset + arc.distance,
                    lateralSlack = lateral,
                    holdForward = holdForward,
                    arc = arc,
                )
                if (best == null || candidate.margin > best.margin) best = candidate
            }
        }
        return best
    }

    private fun preferredSpeed(preferred: Double, low: Double, high: Double, slope: Double): Double {
        val halfWidth = (high - low) * 0.5
        val safety = LANDING_AIM_INSET / slope

        if (safety >= halfWidth) return low + halfWidth
        return preferred.coerceIn(low + safety, high - safety)
    }

    private fun landingWindow(
        from: Stance,
        to: Stance,
        unitX: Double,
        unitZ: Double,
    ): ClosedFloatingPointRange<Double>? {
        val originX = from.x + 0.5
        val originZ = from.z + 0.5
        var near = Double.NEGATIVE_INFINITY
        var far = Double.POSITIVE_INFINITY

        for ((origin, unit, minimum) in listOf(
            Triple(originX, unitX, to.x.toDouble()),
            Triple(originZ, unitZ, to.z.toDouble()),
        )) {
            if (abs(unit) < AXIS_EPSILON) {

                if (origin < minimum - BODY_HALF_WIDTH || origin > minimum + 1.0 + BODY_HALF_WIDTH) return null
                continue
            }
            val first = (minimum - BODY_HALF_WIDTH - origin) / unit
            val second = (minimum + 1.0 + BODY_HALF_WIDTH - origin) / unit
            near = max(near, min(first, second))
            far = min(far, max(first, second))
        }

        return if (far - near > 0.0) near..far else null
    }

    private fun lateralSlack(unitX: Double, unitZ: Double): Double {

        val halfExtent = (abs(unitZ) + abs(unitX)) * 0.5
        return halfExtent - BODY_HALF_WIDTH
    }

    private fun launchOffsets(mode: LaunchMode): List<Double> =
        if (mode.drops) DROP_OFFSETS else JUMP_OFFSETS

    private val JUMP_OFFSETS = listOf(0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.5 + BODY_HALF_WIDTH)

    private val DROP_OFFSETS = listOf(0.5 + BODY_HALF_WIDTH)

    private const val BODY_HALF_WIDTH = 0.3

    private const val LANDING_SAFETY_BLOCKS = 0.2

    private const val LANDING_AIM_INSET = LANDING_SAFETY_BLOCKS + BODY_HALF_WIDTH

    private const val AXIS_EPSILON = 1e-9
}

fun BallisticProfile.momentumSpeed(sprint: Boolean): Double {
    val cruise = cruiseSpeed(sprint)
    val mode = if (sprint) LaunchMode.SPRINT_JUMP else LaunchMode.WALK_JUMP
    val landing = fly(mode, cruise, 0.0) ?: return cruise
    return max(cruise, landing.exitSpeed)
}
