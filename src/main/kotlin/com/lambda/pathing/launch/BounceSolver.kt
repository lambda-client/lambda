package com.lambda.pathing.launch

data class BounceSolution(
    val drop: Int,
    val rise: Int,
    val sprint: Boolean,
    val holdForward: Boolean,

    val speed: Double,

    val speedSlack: Double,

    val launchOffset: Double,

    val contactDistance: Double,
    val arc: ArcSample,
) {
    val airTicks: Int get() = arc.airTicks

    val distance: Double get() = arc.distance

    val rebound: Double get() = arc.heights.last() - arc.heights.min()
}

object BounceSolver {
    fun solve(
        horizontalDistance: Double,
        drop: Int,
        rise: Int,
        profile: BallisticProfile = BallisticProfile.VANILLA,
        sprint: Boolean = true,
        holdForward: Boolean = true,
        maxEntrySpeed: Double = profile.momentumSpeed(sprint),
    ): BounceSolution? {
        if (maxEntrySpeed < 0.0) return null

        val flight = horizontalDistance - LAUNCH_OFFSET
        if (flight <= 0.0) return null

        val base = profile.bounce(0.0, drop, rise, holdForward, sprint) ?: return null
        val unit = profile.bounce(1.0, drop, rise, holdForward, sprint) ?: return null
        val slope = unit.distance - base.distance
        if (slope <= 0.0) return null

        val speed = (flight - base.distance) / slope
        if (speed < 0.0 || speed > maxEntrySpeed) return null

        val arc = profile.bounce(speed, drop, rise, holdForward, sprint) ?: return null

        val slack = (LANDING_WINDOW / 2.0) / slope

        return BounceSolution(
            drop = drop,
            rise = rise,
            sprint = sprint,
            holdForward = holdForward,
            speed = speed,
            speedSlack = minOf(slack, speed),
            launchOffset = LAUNCH_OFFSET,
            contactDistance = arc.distances[troughIndex(arc)],
            arc = arc,
        )
    }

    private fun troughIndex(arc: ArcSample): Int {
        var index = 0
        for (i in arc.heights.indices) if (arc.heights[i] < arc.heights[index]) index = i
        return index
    }

    private const val LANDING_WINDOW = 1.0

    const val LAUNCH_OFFSET = 0.8
}
