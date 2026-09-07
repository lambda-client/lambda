package com.lambda.pathing.launch

data class BounceSolution(
    val drop: Int,
    val rise: Int,
    val sprint: Boolean,
    val holdForward: Boolean,

    /** Launch off the lip with the jump key rather than walking off it. */
    val jump: Boolean = false,

    /** Ticks of forward held after launch; the rest of the arc coasts. */
    val holdTicks: Int = Int.MAX_VALUE,

    val speed: Double,

    val speedSlack: Double,

    val launchOffset: Double,

    val contactDistance: Double,
    val arc: ArcSample,

    /** The real fall depth to the pad's surface; [drop] is only the stance delta. */
    val contactDepth: Double = drop.toDouble(),
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
        jump: Boolean = false,
        holdTicks: Int = Int.MAX_VALUE,
        maxEntrySpeed: Double = profile.momentumSpeed(sprint),

        /** Real fall depth to the pad surface (launch feet to contact feet). */
        contactDepth: Double = drop.toDouble(),

        /** Real landing height above the launch feet. */
        riseHeight: Double = rise.toDouble(),

        /** Ceiling clearance above the launch feet; see [BallisticProfile.bounce]. */
        headroom: Double = Double.POSITIVE_INFINITY,

        /** How far along the ray the body can stand at launch; see [LAUNCH_OFFSET]. */
        launchReach: Double = LAUNCH_OFFSET,
    ): BounceSolution? {
        if (maxEntrySpeed < 0.0) return null

        val flight = horizontalDistance - launchReach
        if (flight <= 0.0) return null

        val base = profile.bounce(0.0, contactDepth, riseHeight, holdForward, sprint, jump, holdTicks, headroom = headroom) ?: return null
        val unit = profile.bounce(1.0, contactDepth, riseHeight, holdForward, sprint, jump, holdTicks, headroom = headroom) ?: return null
        val slope = unit.distance - base.distance
        if (slope <= 0.0) return null

        val speed = (flight - base.distance) / slope
        if (speed < 0.0 || speed > maxEntrySpeed) return null

        val arc = profile.bounce(speed, contactDepth, riseHeight, holdForward, sprint, jump, holdTicks, headroom = headroom) ?: return null

        val slack = (LANDING_WINDOW / 2.0) / slope

        return BounceSolution(
            drop = drop,
            rise = rise,
            sprint = sprint,
            holdForward = holdForward,
            jump = jump,
            holdTicks = holdTicks,
            speed = speed,
            speedSlack = minOf(slack, speed),
            launchOffset = launchReach,
            contactDistance = arc.distances[troughIndex(arc)],
            arc = arc,
            contactDepth = contactDepth,
        )
    }

    /**
     * A launch from REST at the lip, distance dialed in with [BounceSolution.holdTicks]:
     * no entry speed to reproduce (a moving entry amplifies its error ~16x over the
     * glide). Styles tried in preference order; hold found by binary search (distance is
     * monotone in it). See docs/decisions/launch-solver.md (standing starts).
     */
    fun solveStanding(
        horizontalDistance: Double,
        drop: Int,
        rise: Int,
        jump: Boolean,
        sprint: Boolean,
        profile: BallisticProfile = BallisticProfile.VANILLA,
        contactDepth: Double = drop.toDouble(),
        riseHeight: Double = rise.toDouble(),

        /**
         * Judges the arc's CONTACT reach from the stance centre: infinity refuses the
         * hold, finite values rank it. Landing distance alone is not enough on a
         * one-block pad. See docs/decisions/launch-solver.md (contact on the slime).
         */
        contactPenalty: (Double) -> Double = { 0.0 },

        /** Ceiling clearance above the launch feet; see [BallisticProfile.bounce]. */
        headroom: Double = Double.POSITIVE_INFINITY,

        /** How far along the ray the body can stand at launch; see [LAUNCH_OFFSET]. */
        launchReach: Double = LAUNCH_OFFSET,
    ): BounceSolution? {
        val target = horizontalDistance - launchReach
        if (target <= 0.0) return null

        // Sprint needs forward held through the launch tick to be real; a walk-off
        // from rest needs at least a tick of push to leave the lip.
        val minHold = if (sprint) 2 else if (jump) 0 else 1
        fun arcAt(holdTicks: Int): ArcSample? = profile.bounce(
            0.0, contactDepth, riseHeight,
            holdForward = holdTicks > 0, sprint = sprint, jump = jump, holdTicks = holdTicks,
            headroom = headroom,
        )

        var lo = minHold
        var hi = BallisticProfile.MAX_BOUNCE_TICKS
        val shortest = arcAt(lo)?.distance ?: return null
        val longest = arcAt(hi)?.distance ?: return null
        if (target < shortest - LANDING_SUPPORT_REACH || target > longest + LANDING_SUPPORT_REACH) return null
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            val at = arcAt(mid)?.distance
            if (at == null || at < target) lo = mid else hi = mid
        }

        // Every hold whose landing stays inside the support reach is a candidate;
        // among them the contact decides. One tick of hold moves the landing about a
        // quarter block, so the window holds a handful, and on a one-cell pad only
        // some of them put the trough on the slime.
        var chosenHold = -1
        var chosenArc: ArcSample? = null
        var chosenPenalty = Double.POSITIVE_INFINITY
        var chosenError = Double.POSITIVE_INFINITY
        for (hold in (lo - HOLD_SCAN)..(hi + HOLD_SCAN)) {
            if (hold < minHold || hold > BallisticProfile.MAX_BOUNCE_TICKS) continue
            val arc = arcAt(hold) ?: continue
            val error = kotlin.math.abs(arc.distance - target)
            if (error > LANDING_SUPPORT_REACH) continue
            val penalty = contactPenalty(launchReach + arc.distances[troughIndex(arc)])
            if (!penalty.isFinite()) continue
            if (penalty < chosenPenalty || (penalty == chosenPenalty && error < chosenError)) {
                chosenHold = hold
                chosenArc = arc
                chosenPenalty = penalty
                chosenError = error
            }
        }
        val arc = chosenArc ?: return null

        return BounceSolution(
            drop = drop,
            rise = rise,
            sprint = sprint,
            holdForward = chosenHold > 0,
            jump = jump,
            holdTicks = chosenHold,
            speed = 0.0,
            speedSlack = STANDING_REST_SPEED,
            launchOffset = launchReach,
            contactDistance = arc.distances[troughIndex(arc)],
            arc = arc,
            contactDepth = contactDepth,
        )
    }

    /** Holds to scan either side of the landing-exact hold for a pad-centred contact. */
    private const val HOLD_SCAN = 3

    /**
     * How far from the landing cell's CENTRE a standing arc may put the feet: half a cell
     * plus the body's half-width. The moving-entry family keeps the tighter
     * [LANDING_WINDOW], whose half also prices entry-speed slack.
     * See docs/decisions/launch-solver.md (support reach).
     */
    private const val LANDING_SUPPORT_REACH = 0.8

    /** (jump, sprint) standing families, gentlest drift first: the gentler the
     *  pre-contact glide, the closer the contact stays to the lip, and a contact
     *  deep in the pit is what clears the far wall on the way back up. */
    val STANDING_STYLES = listOf(
        true to false,
        false to false,
        true to true,
        false to true,
    )

    /** A standing launch tolerates this much residual creep speed at the lip. */
    const val STANDING_REST_SPEED = 0.02

    private fun troughIndex(arc: ArcSample): Int {
        var index = 0
        for (i in arc.heights.indices) if (arc.heights[i] < arc.heights[index]) index = i
        return index
    }

    private const val LANDING_WINDOW = 1.0

    /**
     * Default standable reach along the ray from the launch cell's centre: half a FULL
     * BLOCK plus the body's half-width. Partial supports reach less (a fence post 0.425)
     * and the probe passes the real reach. See docs/decisions/launch-solver.md.
     */
    const val LAUNCH_OFFSET = 0.8
}
