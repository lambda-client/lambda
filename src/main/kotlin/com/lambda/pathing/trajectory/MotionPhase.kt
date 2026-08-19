/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.util.player.prediction.MovementSimulationState
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot

/**
 * Whether a frame is somewhere a plan can be re-entered.
 *
 * A [TRIM] frame is a steady state the body settles back into: grounded, cruising, and
 * pointed where it is already going. Small errors there decay, because the controller
 * steering the next stretch aims at a world target and simply corrects toward it.
 *
 * A [MANEUVER] frame is anything mid-commitment — airborne, launching, turning hard,
 * accelerating, scraping a wall. Errors there do not decay: a ballistic arc launched a
 * tenth of a block further along lands a tenth of a block further along, and a one-block
 * pad does not forgive that.
 *
 * The distinction is the measured one. Re-running a tail's decisions from a quarter-block
 * error survived 7 of 7 times where the tail was walking, and 4 of 9 where it contained
 * jumps. Restricting splices to trims is what turns that second number into the first.
 */
enum class MotionPhase {
    TRIM,
    MANEUVER,
}

object MotionPhases {
    /** Below this the body is starting or stopping, not cruising. */
    const val MIN_SPEED = 0.15

    /** Speed still changing by more than this per tick is an acceleration, not a trim. */
    const val MAX_SPEED_DRIFT = 0.01

    /** Bearing still swinging by more than this per tick is a turn, not a trim. */
    const val MAX_HEADING_DEGREES = 4.0

    /**
     * A frame this close in front of a jump is already a launch approach.
     *
     * Rejoining there shifts the takeoff point, which is exactly the error a jump cannot
     * absorb, even though the body is grounded and cruising and looks like a trim.
     */
    const val LAUNCH_LEAD_FRAMES = 6

    fun classify(
        initial: MovementSimulationState,
        frames: List<SimulatedTrajectoryFrame>,
    ): List<MotionPhase> = frames.indices.map { index ->
        if (isTrim(initial, frames, index)) MotionPhase.TRIM else MotionPhase.MANEUVER
    }

    fun isTrim(
        initial: MovementSimulationState,
        frames: List<SimulatedTrajectoryFrame>,
        index: Int,
    ): Boolean {
        val current = frames[index].state
        val previous = if (index == 0) initial else frames[index - 1].state
        if (!current.onGround || !previous.onGround) return false
        if (current.horizontalCollision || previous.horizontalCollision) return false

        val speed = hypot(current.velocity.x, current.velocity.z)
        if (speed < MIN_SPEED) return false
        if (abs(speed - hypot(previous.velocity.x, previous.velocity.z)) > MAX_SPEED_DRIFT) return false
        if (headingChangeDegrees(previous, current) > MAX_HEADING_DEGREES) return false

        val lead = minOf(index + LAUNCH_LEAD_FRAMES, frames.lastIndex)
        return (index..lead).none { frames[it].input.jump }
    }

    private fun headingChangeDegrees(
        previous: MovementSimulationState,
        current: MovementSimulationState,
    ): Double {
        val before = hypot(previous.velocity.x, previous.velocity.z)
        val after = hypot(current.velocity.x, current.velocity.z)
        if (before <= 1e-6 || after <= 1e-6) return 180.0
        val cosine = (previous.velocity.x * current.velocity.x + previous.velocity.z * current.velocity.z) /
            (before * after)
        return Math.toDegrees(acos(cosine.coerceIn(-1.0, 1.0)))
    }
}
