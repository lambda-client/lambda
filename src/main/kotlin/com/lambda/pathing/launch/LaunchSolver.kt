/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.launch

import com.lambda.pathing.coarse.Stance
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * A take-off that has been solved rather than guessed.
 *
 * [speed] is the stored horizontal velocity the body must carry into the take-off tick,
 * and [speedSlack] is how far either side of it still lands on the target. That margin is
 * the useful part: it turns "this jump might work" into an ordering, so the search spends
 * its rollouts on robust arcs before desperate ones.
 */
data class LaunchSolution(
    val mode: LaunchMode,
    /** How far past the stance centre the body leaves the ground, in blocks. */
    val launchOffset: Double,
    val speed: Double,
    /** Tolerated take-off speed error either side of [speed], in blocks per tick. */
    val speedSlack: Double,
    /** How far the aim sits from the nearest edge of the landing window, in blocks. */
    val landingSlack: Double,
    /** Fraction of the body's achievable speed still unspent at take-off, in 0..1. */
    val headroom: Double,
    /** Distance from the stance centre to the aimed landing point. */
    val aimDistance: Double,
    val lateralSlack: Double,
    /**
     * Whether the body holds forward through the flight.
     *
     * A drop that must land close releases instead; holding it would carry the body past
     * the pad. The control program has to be told, because the arc it was solved from
     * assumed one policy or the other.
     */
    val holdForward: Boolean,
    /** The arc actually flown at [speed], for the obstacle sweep and the cost model. */
    val arc: ArcSample,
    val clearance: Double = 0.0,
) {
    val sprint: Boolean get() = mode.sprint

    val jumps: Boolean get() = mode.jumps

    val airTicks: Int get() = arc.airTicks

    val apex: Double get() = arc.apex

    /** How far the body flies from where it leaves the ground to where it touches down. */
    val flightDistance: Double get() = aimDistance - launchOffset

    fun withClearance(clearance: Double) = copy(clearance = clearance)

    /**
     * Ranking margin: how much has to go right for this launch to work.
     *
     * Three things independently make a launch fragile, and all three belong here.
     * [landingSlack] dominates: it is how far the aim sits from falling off the target, and
     * it is directly comparable to the arc model's own error. [lateralSlack] is geometry
     * and varies little between candidates. [headroom] separates two launches with
     * identical tolerance -- a target the same size always tolerates the same error, so
     * what makes a long jump harder than a short one is that it must be taken near the
     * body's top speed, where any loss is unrecoverable.
     *
     * All three are in blocks, deliberately. Ranking used [speedSlack] here, which is in
     * blocks *per tick*, so a launch whose speed band was narrow in time but pinned against
     * the edge of the landing window could still out-rank one aimed at the middle of it --
     * and the drop that follows from that lands a hand's width past the pad.
     *
     * [headroom] changes sign between the two families, because unspent speed means
     * opposite things to them. A jump that leaves with speed to spare can afford to lose
     * some and still land, so headroom is slack. A drop cannot spend it: the take-off speed
     * is fixed by where the pad is, and any excess has to be *shed* on the approach. So the
     * mode with the most headroom is the one that has to brake hardest.
     *
     * Rewarding it on both put a sprint in front of a walk for a drop whose solved take-off
     * speed was below walking pace -- a gait chosen precisely because it was wrong for the
     * job. The control program then had to fight it, releasing and re-pressing forward to
     * hold a crawl, which chatters the sprint key and drives vanilla's double-tap-to-sprint
     * into a corner the simulator does not agree about tick for tick. That disagreement
     * shows up live as a certified tape aborting on a sprinting-flag deviation.
     */
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
        /**
         * Best first, and a sprint never wins a tie.
         *
         * Two modes routinely solve to the same arc, because a drop flown with forward
         * released does not care whether the sprint key is down -- the body is coasting
         * either way. Left to the enum order the sprinting one came first and the control
         * program dutifully held a key that changed nothing about the flight and everything
         * about the `sprinting` flag, which is compared frame by frame during replay. A
         * gait that buys no distance is pure risk, so it goes last.
         */
        val BEST_FIRST: Comparator<LaunchSolution> =
            compareByDescending<LaunchSolution> { it.margin }.thenBy { it.sprint }

        private const val LATERAL_MARGIN_WEIGHT = 0.25
        private const val HEADROOM_WEIGHT = 0.1
    }
}

/**
 * Inverts a ballistic arc onto a target block.
 *
 * The question this answers is not "can the body reach that block" but "where in that
 * block should it aim, and what does it have to be doing at take-off to get there". Those
 * are different questions, and only the second one produces a control input.
 *
 * The method is exact rather than fitted. [BallisticProfile.fly] establishes that air
 * ticks do not depend on horizontal speed and that distance is affine in it, so two
 * sample flights determine the whole distance-versus-speed line. Clipping the flight ray
 * against the target block's footprint gives the interval of distances that land on it;
 * pushing that interval back through the line gives the interval of take-off speeds; the
 * midpoint is the aim and the half-width is the margin.
 */
object LaunchSolver {
    /**
     * Solves every mode that can make this move, best margin first.
     *
     * [maxEntrySpeed] bounds what the body may be doing at take-off. Callers that know the
     * real state should pass it; the default admits the momentum a chained jump carries,
     * which is nearly twice ground cruise and is what makes parkour chains possible.
     */
    fun solve(
        from: Stance,
        to: Stance,
        profile: BallisticProfile = BallisticProfile.VANILLA,
        modes: List<LaunchMode> = LaunchMode.entries,
        maxEntrySpeed: (LaunchMode) -> Double = { profile.momentumSpeed(it.sprint) },
        preferredEntrySpeed: (LaunchMode) -> Double = { profile.cruiseSpeed(it.sprint) },
    ): List<LaunchSolution> {
        val dx = (to.x - from.x).toDouble()
        val dz = (to.z - from.z).toDouble()
        val length = hypot(dx, dz)
        if (length <= 1e-9) return emptyList()
        val unitX = dx / length
        val unitZ = dz / length
        val rise = to.y - from.y

        // The target's footprint seen along the flight line. Both are properties of the
        // geometry alone, so they are computed once for every mode and offset.
        val lateral = lateralSlack(unitX, unitZ)
        if (lateral < 0.0) return emptyList()

        return modes.filter { it.supports(rise) }.mapNotNull { mode ->
            solveMode(
                from, to, unitX, unitZ, rise, lateral, mode, profile,
                maxEntrySpeed(mode), preferredEntrySpeed(mode),
            )
        }.sortedWith(LaunchSolution.BEST_FIRST)
    }

    /** The single best launch for this move, or null when no mode can make it. */
    fun best(
        from: Stance,
        to: Stance,
        profile: BallisticProfile = BallisticProfile.VANILLA,
        modes: List<LaunchMode> = LaunchMode.entries,
        maxEntrySpeed: (LaunchMode) -> Double = { profile.momentumSpeed(it.sprint) },
        preferredEntrySpeed: (LaunchMode) -> Double = { profile.cruiseSpeed(it.sprint) },
    ): LaunchSolution? =
        solve(from, to, profile, modes, maxEntrySpeed, preferredEntrySpeed).firstOrNull()

    private fun solveMode(
        from: Stance,
        to: Stance,
        unitX: Double,
        unitZ: Double,
        rise: Int,
        lateral: Double,
        mode: LaunchMode,
        profile: BallisticProfile,
        maxEntrySpeed: Double,
        preferredEntrySpeed: Double,
    ): LaunchSolution? {
        if (maxEntrySpeed < 0.0) return null

        val window = landingWindow(from, to, unitX, unitZ) ?: return null

        var best: LaunchSolution? = null
        // Holding forward is the natural gait and flies furthest; coasting is the only way
        // to land short. A jump is always flown held -- letting go mid-arc buys nothing and
        // costs the whole approach -- so only a drop is offered the choice.
        val policies = if (mode.drops) listOf(true, false) else listOf(true)

        for (holdForward in policies) {
            // Two sample flights fix the affine distance line. Air ticks and the height
            // profile are shared because vertical motion ignores horizontal speed entirely.
            val base = profile.fly(mode, 0.0, rise, holdForward) ?: continue
            val unit = profile.fly(mode, 1.0, rise, holdForward) ?: continue
            val slope = unit.distance - base.distance
            if (slope <= 1e-9) continue

            for (offset in launchOffsets(mode)) {
                // Leaving the ground further into the cell shortens the flight that remains.
                fun speedFor(distance: Double) = (distance - offset - base.distance) / slope

                val feasibleLow = max(speedFor(window.start), 0.0)
                val feasibleHigh = min(speedFor(window.endInclusive), maxEntrySpeed)
                if (feasibleHigh < feasibleLow) continue

                val speed = preferredSpeed(preferredEntrySpeed, feasibleLow, feasibleHigh, slope)
                // Re-fly at the speed actually chosen. The affine line placed the landing,
                // but the sweep needs the real per-tick positions of *this* arc, not the
                // zero-speed sample the line was derived from.
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

    /**
     * Where in the feasible band to aim.
     *
     * The nearest speed to [preferred] that still lands a safe distance inside the window.
     * Both halves of that matter, and both were learned the hard way.
     *
     * Aiming at the window edge is what a "hold your gait, land wherever" rule does, and it
     * put a one-block step down six centimetres from the pad edge -- inside the arc model's
     * own error, and with a third of the body already hanging over it, because the window
     * bounds the body's *centre*. Aiming at the window *centre* regardless is worse in the
     * other direction:
     * a three-block jump has its centre below sprint cruise, so a cruising body is told to
     * slow down for it, and the search spends thirty frames and three hundred degrees of
     * turning arranging to be slower.
     *
     * So: inset the window by a fixed distance, and inside what remains take whatever is
     * closest to the gait the body already has. When the band is narrower than twice the
     * inset the two bounds meet at its midpoint, which is the right answer for a tight
     * launch anyway.
     */
    private fun preferredSpeed(preferred: Double, low: Double, high: Double, slope: Double): Double {
        val halfWidth = (high - low) * 0.5
        val safety = LANDING_AIM_INSET / slope
        // Taking the midpoint explicitly rather than letting the two insets meet: in
        // floating point they cross rather than meet, and an empty range throws.
        if (safety >= halfWidth) return low + halfWidth
        return preferred.coerceIn(low + safety, high - safety)
    }

    /**
     * The interval along the flight ray whose points lie over the target block.
     *
     * A slab clip rather than a distance comparison, because a diagonal move crosses the
     * target's square off-axis and the usable stretch of it is neither one block long nor
     * centred on the block centre. Aiming at the middle of *this* is what the old
     * centre-plus-fixed-tolerance rule could not express.
     *
     * These are hard bounds on where the body's *centre* may come down, deliberately not
     * shrunk. Insetting here refuses moves that are genuinely available -- a span-5 jump
     * is only makeable landing on the near lip -- and this layer's contract is to stay
     * permissive and let the trajectory search do the refusing.
     *
     * The body's width is accounted for in the aim instead, by [LANDING_AIM_INSET].
     */
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
                // The ray never crosses this axis' slab, so it either starts inside it or
                // misses the block entirely.
                if (origin < minimum || origin > minimum + 1.0) return null
                continue
            }
            val first = (minimum - origin) / unit
            val second = (minimum + 1.0 - origin) / unit
            near = max(near, min(first, second))
            far = min(far, max(first, second))
        }

        return if (far - near > 0.0) near..far else null
    }

    /**
     * Half the target's extent across the flight line, less the body's half width.
     *
     * Negative means the body is wider than the target measured that way, which no launch
     * can fix.
     */
    private fun lateralSlack(unitX: Double, unitZ: Double): Double {
        // Support function of a unit square along the perpendicular direction.
        val halfExtent = (abs(unitZ) + abs(unitX)) * 0.5
        return halfExtent - BODY_HALF_WIDTH
    }

    /**
     * Where the body leaves the ground, in blocks past the stance centre.
     *
     * A jump chooses this: the key can be pressed anywhere on the block, and which tick it
     * is pressed on is the search's most useful control. A drop does not choose it. The
     * body stays supported until its box clears the take-off block's footprint, which
     * happens half a block past the centre plus its own half width -- so a drop is already
     * committed 0.8 blocks along before it starts falling, and pretending otherwise is
     * what makes a walk-off land a block short of where it was aimed.
     */
    private fun launchOffsets(mode: LaunchMode): List<Double> =
        if (mode.drops) DROP_OFFSETS else JUMP_OFFSETS

    private val JUMP_OFFSETS = listOf(0.0, 0.1, 0.2, 0.3, 0.4, 0.5)

    private val DROP_OFFSETS = listOf(0.5 + BODY_HALF_WIDTH)

    private const val BODY_HALF_WIDTH = 0.3

    /**
     * How far inside the landing window the aim is kept, in blocks.
     *
     * Sized against the arc model's own disagreement with the simulator, which runs to
     * about a tenth of a block over a long jump.
     */
    private const val LANDING_SAFETY_BLOCKS = 0.2

    /**
     * How far inside the landing window the aim is kept, body width included.
     *
     * [landingWindow] bounds where the body's *centre* may come down, so an aim that only
     * respected [LANDING_SAFETY_BLOCKS] still put three tenths of the body past the pad.
     * On a wide landing that is invisible. On a one-block tread it is the difference
     * between standing on it and sliding off it: [preferredSpeed] takes the fastest gait
     * the window allows, so every drop onto a narrow tread was aimed at the far lip and
     * carried its landing speed straight over the edge -- which is why a lone drop onto a
     * deck worked while a staircase of them did not.
     *
     * This is the same correction [lateralSlack] already applies across the flight line.
     * Subtracting the half width there and not here treated the body as a box across its
     * direction of travel and a point along it.
     */
    private const val LANDING_AIM_INSET = LANDING_SAFETY_BLOCKS + BODY_HALF_WIDTH

    private const val AXIS_EPSILON = 1e-9
}

/**
 * The fastest a take-off can realistically be entered.
 *
 * Ground cruise is not the answer: landing from a sprint jump leaves the body at roughly
 * 1.9x cruise, because air drag is 0.91 against 0.546 on the ground, and that surplus is
 * exactly what a chained parkour jump spends. Capping at cruise would refuse jumps that
 * are made in game every day.
 */
fun BallisticProfile.momentumSpeed(sprint: Boolean): Double {
    val cruise = cruiseSpeed(sprint)
    val mode = if (sprint) LaunchMode.SPRINT_JUMP else LaunchMode.WALK_JUMP
    val landing = fly(mode, cruise, 0) ?: return cruise
    return max(cruise, landing.exitSpeed)
}
