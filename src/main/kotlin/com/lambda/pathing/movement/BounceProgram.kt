/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.movement

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.launch.BounceSolution
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Falls onto slime at a solved speed and rides the rebound out.
 *
 * Almost all of the work is before the lip, as it is for a drop: the arc is decided by the
 * speed the body leaves with, and thirty ticks of flight give air control no chance to fix a
 * bad one. What is different from a drop is that there is nothing to slow down *for* -- a
 * bounce wants speed, because the reach is the reason to take it.
 *
 * Three things the control must not do, each of which silently turns the move into an
 * ordinary fall into a pit:
 *
 * - **Never sneak.** `bypassesLandingEffects()` is `isSneaking()`, so a sneaking body lands
 *   dead on the slime. The drop control presses sneak to pin itself at a ledge, and doing
 *   that here would cancel the very thing being planned for.
 * - **Never jump.** The impulse is the fall's. Adding height only lengthens the flight and
 *   changes the arc the solver certified.
 * - **Match the solved forward policy.** The arc was integrated either holding forward or
 *   coasting, and flying the other one is flying a different arc.
 */
internal class BounceProgram(
    private val takeoff: HorizontalPoint,
    private val aim: HorizontalPoint,
    private val solution: BounceSolution,
    private val maxYawChange: Double,
) : ControlProgram {
    private var airborne = false
    private var landed = false
    private var bounced = false

    override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
        // Touching the slime is a grounded frame in the middle of the flight, so "has it
        // landed" cannot be "is it on the ground". The rebound is what separates the two: a
        // body that has been grounded and is now rising again is mid-bounce, not finished.
        if (!observed.onGround) {
            airborne = true
        } else if (airborne) {
            if (observed.velocity.y > REBOUND_SPEED) bounced = true else landed = true
        }

        val desiredYaw = yawTowards(aim, observed)
        val yawDelta = yawStepTowards(desiredYaw, observed, maxYawChange)
        val rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch)

        val forward = when {
            landed -> 0.0
            !airborne -> approachForward(observed)
            solution.holdForward -> 1.0
            else -> 0.0
        }

        return MovementSimulationInput(
            forward = forward,
            strafe = if (airborne && !landed) airborneStrafe(observed) else 0.0,
            sprint = solution.sprint && forward > 0.0,
            jump = false,
            sneak = false,
            rotation = rotation,
        )
    }

    /**
     * Holds the approach until the body is at the solved leave speed, then coasts.
     *
     * The opposite sense to a drop's brake: a bounce is aiming *at* a speed rather than at
     * rest, and the speed is usually most of what the body can carry. Coasting once it is
     * there keeps the leave inside the solved window instead of overshooting it, and needs no
     * brake at all -- so no sneak, which would cancel the bounce.
     */
    private fun approachForward(observed: MovementSimulationState): Double =
        if (observed.velocity.horizontalLength() >= solution.speed + solution.speedSlack) 0.0 else 1.0

    /**
     * Steers the flight back onto its line while airborne.
     *
     * Worth more here than on a drop simply because the flight is longer: thirty ticks of
     * lateral drift off a solved heading misses a one-block pad by more than its width, and
     * strafe is the one axis air control can spend freely -- it changes no along-track
     * distance the arc was solved for.
     */
    private fun airborneStrafe(observed: MovementSimulationState): Double {
        val offset = lateralOffset(observed)
        if (abs(offset) < LATERAL_DEADBAND) return 0.0
        return if (offset > 0.0) 1.0 else -1.0
    }

    private fun lateralOffset(observed: MovementSimulationState): Double {
        val dx = aim.x - takeoff.x
        val dz = aim.z - takeoff.z
        val length = hypot(dx, dz)
        if (length <= 1e-9) return 0.0
        val px = observed.position.x - takeoff.x
        val pz = observed.position.z - takeoff.z
        return (dx * pz - dz * px) / length
    }

    private companion object {
        /**
         * Upward speed that distinguishes a rebound from a landing, in blocks per tick.
         *
         * A slime rebound leaves at most of the speed the fall arrived with, which is a long
         * way above anything a settling body shows -- so this only has to be clear of zero.
         */
        const val REBOUND_SPEED = 0.1

        /** Lateral error tolerated before the airborne trim strafes, in blocks. */
        const val LATERAL_DEADBAND = 0.05
    }
}
