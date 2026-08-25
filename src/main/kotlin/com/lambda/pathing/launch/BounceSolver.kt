/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.launch

/**
 * A solved fall onto slime and the flight back out of it.
 *
 * [contactDistance] is what a bounce needs and a jump does not: the slime has to actually be
 * where the body arrives, so the edge carries the point along its own heading where the
 * trough happens rather than leaving the caller to guess it.
 */
data class BounceSolution(
    val drop: Int,
    val rise: Int,
    val sprint: Boolean,
    val holdForward: Boolean,
    /** Stored horizontal velocity the body must carry into the take-off tick. */
    val speed: Double,
    /** Tolerated take-off speed error either side of [speed], in blocks per tick. */
    val speedSlack: Double,
    /** How far past the stance centre the body leaves the ground, in blocks. */
    val launchOffset: Double,
    /** Horizontal distance from the take-off to the point where the body meets the slime. */
    val contactDistance: Double,
    val arc: ArcSample,
) {
    val airTicks: Int get() = arc.airTicks

    /** Total horizontal distance from take-off to landing. */
    val distance: Double get() = arc.distance

    /** How far the body climbs back out of the trough it landed in. */
    val rebound: Double get() = arc.heights.last() - arc.heights.min()
}

/**
 * Solves the entry speed that makes a bounce land where it is aimed.
 *
 * The same inversion the jump solver uses, and for the same reason: vertical motion does not
 * depend on horizontal speed, so the tick the body meets the slime and the tick it lands are
 * both fixed by the geometry alone. With the timing fixed, distance is affine in take-off
 * speed, so the speed that covers a required distance is computed rather than searched for.
 *
 * That the bounce leaves the arithmetic affine is not obvious and is worth stating: the
 * reflection touches only the vertical component, and the one ground tick it introduces
 * afterwards is an affine step like every other. So two samples still determine the line.
 */
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
        // The stance centre is not the take-off point. The body is 0.6 wide and its stance is
        // the middle of a one-block cell, so it is still supported until it has travelled 0.8
        // past that centre -- and an arc begun any earlier descends through the very deck it
        // is leaving, which the obstacle sweep quite correctly refuses.
        val flight = horizontalDistance - LAUNCH_OFFSET
        if (flight <= 0.0) return null

        val base = profile.bounce(0.0, drop, rise, holdForward, sprint) ?: return null
        val unit = profile.bounce(1.0, drop, rise, holdForward, sprint) ?: return null
        val slope = unit.distance - base.distance
        if (slope <= 0.0) return null

        val speed = (flight - base.distance) / slope
        if (speed < 0.0 || speed > maxEntrySpeed) return null

        val arc = profile.bounce(speed, drop, rise, holdForward, sprint) ?: return null

        // How far the take-off speed may be wrong and still land on the block aimed at. One
        // block of landing window shared either side of the aim, converted back into speed
        // through the same slope -- the inverse being exact is what makes this a margin
        // rather than a guess.
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

    /** The tick the body is deepest, which is the tick it is touching the slime. */
    private fun troughIndex(arc: ArcSample): Int {
        var index = 0
        for (i in arc.heights.indices) if (arc.heights[i] < arc.heights[index]) index = i
        return index
    }

    /** A landing block is one block across, and the aim sits in the middle of it. */
    private const val LANDING_WINDOW = 1.0

    /**
     * How far past the stance centre the body is still held up, in blocks.
     *
     * Half a cell to reach its edge plus half the body's width to clear it. The same figure
     * the drop solver arrives at, and for the same reason.
     */
    const val LAUNCH_OFFSET = 0.8
}
