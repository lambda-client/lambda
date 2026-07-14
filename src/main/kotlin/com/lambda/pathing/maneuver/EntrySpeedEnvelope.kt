/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.maneuver

/**
 * Worker-validated launch-state box for a discovered maneuver (T3): the
 * entry speeds (blocks/tick along the segment) and the takeoff-progress
 * window (blocks past the takeoff node center) the validation sims proved
 * to land. Position is part of the entry state on equal footing with
 * speed — a maximum-distance jump may only work from a deep, lip-adjacent
 * launch (minTakeoffProgress > 0), exactly like human parkour play.
 *
 * Whole-band edges publish NO envelope and are executed exactly as before
 * envelopes existed; the presence of one marks a momentum-critical edge.
 */
data class EntrySpeedEnvelope(
    val min: Double,
    val max: Double,
    val minTakeoffProgress: Double = 0.0,
    val maxTakeoffProgress: Double = 0.45,
    /**
     * Whether the validated flight was a SPRINT jump.
     *
     * Sprint is part of the entry state, not a global preference. A
     * sprint-jump's launch boost alone carries ~3.5 blocks, so a short hop
     * onto a narrow landing is impossible at sprint *at every entry speed* —
     * a human walks those, and so must the agent. Discovery used to simulate
     * sprint unconditionally, which is why short jumps onto single-block pads
     * could never be admitted: the validator overflew the pad every time.
     */
    val sprint: Boolean = true,
) {
    init {
        require(min >= 0.0 && max >= min) { "Invalid entry-speed envelope [$min, $max]" }
        require(minTakeoffProgress >= 0.0 && maxTakeoffProgress >= minTakeoffProgress) {
            "Invalid takeoff window [$minTakeoffProgress, $maxTakeoffProgress]"
        }
    }

    fun contains(speed: Double, tolerance: Double = 0.0): Boolean =
        speed >= min - tolerance && speed <= max + tolerance
}
