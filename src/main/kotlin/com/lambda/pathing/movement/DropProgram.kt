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
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Descends a ledge under control, rather than falling off the end of a walk.
 *
 * The problem this solves is not steering, it is *speed*. A drop's landing window is
 * fixed by geometry the moment the body leaves the lip, and by then it is too late: air
 * control adds 0.02 blocks per tick against a commitment of a block and a half. So almost
 * all of the work happens before take-off, regulating the approach to the speed
 * [LaunchSolution] says lands on the pad, and the airborne phase only trims.
 *
 * Four phases, in order:
 *
 * 1. **approach** -- steer at the aim and hold the solved leave speed, coasting whenever
 *    the body is running hot. This is the phase a [SegmentFollowerProgram] cannot express
 *    at all: it holds forward until something stops it.
 * 2. **leave** -- no jump. The apex is the take-off height, so the fall is the shortest
 *    the geometry allows, which is what keeps a deep drop inside the safe fall distance.
 * 3. **airborne** -- keep pushing while short of the aim, release once past it.
 * 4. **settle** -- release on touchdown so the body does not slide off a narrow pad.
 */
internal class DropProgram(
    private val takeoff: HorizontalPoint,
    private val aim: HorizontalPoint,
    private val solution: LaunchSolution,
    private val maxYawChange: Double,
) : ControlProgram {
    private val flightLength = horizontalDistance(takeoff, aim)

    private var airborne = false
    private var landed = false
    private var aligned = false
    private var pinnedTicks = 0
    private var slowestPinnedSpeed = Double.POSITIVE_INFINITY
    private var previousAlong = Double.NaN
    private var previousStep = 0.0

    override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
        if (!observed.onGround) airborne = true else if (airborne) landed = true

        val desiredYaw = yawTowards(aim, observed)
        val yawDelta = yawStepTowards(desiredYaw, observed, maxYawChange)
        val rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch)

        val approaching = !airborne && !landed
        val sneak = approaching && pinningToLip(observed)

        val forward = when {
            landed -> 0.0
            airborne -> airborneForward(observed)

            // Once the brake has done its work, holding forward undoes it. The body needs
            // a tick or two of walking to actually leave the block after the key comes off,
            // and at the full walking acceleration that is enough to put it back over the
            // solved leave speed -- which is how a braked approach still took off hot and
            // landed on the far lip of the tread instead of on it.
            //
            // Released, the same ticks coast at the speed the pin left. This is one release
            // and no press, so it is not a tap: the double-tap window only cares about a
            // press that follows a release, and a coasting solution never presses again.
            aligned && !solution.holdForward -> 0.0

            // Held for the rest of the approach. The gait is regulated by pinning on the
            // lip, not by letting go of the key, so there is nothing to tap.
            else -> 1.0
        }

        return MovementSimulationInput(
            forward = forward,
            strafe = if (airborne && !landed) airborneStrafe(observed) else 0.0,
            sprint = solution.sprint && forward > 0.0 && !sneak,
            jump = false,
            sneak = sneak,
            rotation = rotation,
        )
    }

    /**
     * Pins the body on the lip with sneak until it is slow enough to step off.
     *
     * Vanilla lags the two halves of sneaking differently, and the whole technique lives in
     * that gap. `clipAtLedge` reads the sneak key live, so the ledge clip bites on the tick
     * it is pressed; `shouldSlowDown` reads the sneaking *pose*, latched before the input
     * was taken, so the speed multiplier only arrives a tick later. Sneaking is therefore a
     * poor brake and an immediate handbrake, and this uses it as the second.
     *
     * So the approach never regulates its gait at all. It walks at whatever speed it likes,
     * and on the tick it would cross the lip the clip holds the body on the edge -- it
     * cannot leave a block it is sneaking off. The tick it is inside the solved window the
     * key comes off and the body steps over.
     *
     * What actually brings the speed down while it is pinned is worth being exact about,
     * because it is not the clip. The clip clamps the *movement* and leaves the velocity
     * untouched; what slows the body is the sneaking pose cutting the input acceleration to
     * a third once it arrives a tick later. So the speed decays geometrically toward the
     * sneak-walk terminal speed and then stops. It never reaches zero, and below that
     * terminal there is no speed this instrument can deliver.
     *
     * That is why the release has two conditions and not one. Inside the window, release --
     * the point of the manoeuvre. Converged but still above it, release anyway: the brake
     * has given everything it has, and holding on is holding forever. The alternative is a
     * control that never lets go, which is not a slow drop, it is no drop at all. What comes
     * out is a deterministic tape either way, and the search is what decides whether the
     * arc it produced is one worth walking.
     *
     * What this replaces is a brake built out of the forward key, which had to be released
     * to slow down and then held down for vanilla's whole double-tap window before it could
     * be pressed again -- most of a second of standing still for every correction.
     */
    private fun pinningToLip(observed: MovementSimulationState): Boolean {
        if (aligned) return false
        if (!atLip(observed)) return false

        val speed = observed.velocity.horizontalLength()
        if (speed <= solution.speed + SPEED_TOLERANCE) {
            aligned = true
            return false
        }

        // The pose that does the braking is latched from *last* tick's sneak, so the first
        // pinned tick still accelerates at the full walking rate and the speed goes up
        // before it comes down. Judging convergence before the pose has arrived reads that
        // rise as "the brake has stopped working" and lets go on the tick it was pressed.
        if (pinnedTicks >= POSE_LAG_TICKS && speed > slowestPinnedSpeed - PIN_CONVERGENCE) {
            aligned = true
            return false
        }

        pinnedTicks++
        slowestPinnedSpeed = minOf(slowestPinnedSpeed, speed)
        return true
    }

    /**
     * Whether this tick's movement would carry the body over the take-off point.
     *
     * Measured from where the body actually went last tick rather than read off its stored
     * velocity, and the difference is a factor of about two. Vanilla applies ground friction
     * to the velocity *after* the move, so the velocity carried in a state is the movement
     * that has already happened multiplied by 0.546 -- it understates the next step by
     * nearly half. Asking for the ledge with that number arrives a tick late, which is a
     * tick after the body has left the block: the clip then pins a body already hanging in
     * the air, so it holds for exactly one tick and takes off at full walking speed.
     *
     * The travelled distance needs no physics constants and stays correct on ice or soul
     * sand, where a friction constant baked in here would not.
     */
    private fun atLip(observed: MovementSimulationState): Boolean {
        val along = alongEdge(takeoff, aim, observed.position.x, observed.position.z)
        val step = if (previousAlong.isNaN()) 0.0 else along - previousAlong
        previousAlong = along
        previousStep = maxOf(step, previousStep * STEP_DECAY)

        val reach = maxOf(previousStep, observed.velocity.horizontalLength())
        return -along <= reach + LIP_MARGIN
    }

    /**
     * Trims the arc with what little authority air control has.
     *
     * The solved policy decides whether there is anything to trim *along* the flight. A drop
     * aimed at a close pad was solved on the coasting arc, and pushing forward here would
     * fly it straight past the landing the solver picked -- so a coasting solution stays
     * released, and only a held one pushes while it is still short. Deviating from the
     * policy the arc was solved with flies a different arc than the one that was certified.
     *
     * Forward is also the one key that cannot be trimmed with freely: a release followed by
     * a press is vanilla's double-tap-to-sprint, and a flight is five to nine ticks, so any
     * mid-air correction on this axis is a textbook double tap a couple of ticks wide.
     */
    private fun airborneForward(observed: MovementSimulationState): Double {
        if (!solution.holdForward) return 0.0
        val travelled = alongEdge(takeoff, aim, observed.position.x, observed.position.z)
        return if (travelled < flightLength) 1.0 else 0.0
    }

    /**
     * Steers the body back onto the flight line while it is in the air.
     *
     * The one axis air control can be spent on freely. Yaw alone cannot fix lateral drift on
     * a coasting drop, because a body with no key held does not care which way it is facing
     * -- and drift is not hypothetical: a descent that leaves the lip a few hundredths off
     * the line lands a third of a block to the side of the pad it was aimed at, which is the
     * difference between a one-block tread and thin air.
     *
     * Strafe is used rather than forward because forward is the key the double-tap sprint
     * window watches. Strafing changes no along-track distance the arc was solved for, so a
     * coasting solution stays a coasting solution while still being steerable.
     */
    private fun airborneStrafe(observed: MovementSimulationState): Double {
        val offset = lateralOffset(observed)
        if (abs(offset) < LATERAL_DEADBAND) return 0.0
        return if (offset > 0.0) 1.0 else -1.0
    }

    /** Signed distance from the take-off to aim line, positive to the line's left. */
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
         * Slack around the solved leave speed before the approach coasts.
         *
         * Roughly a twentieth of sprint cruise: tight enough that the leave lands inside
         * the solved window, loose enough that the input does not chatter every tick.
         */
        const val SPEED_TOLERANCE = 0.015

        /**
         * Speed the pin must shed each tick to be worth holding, in blocks per tick.
         *
         * A convergence test, not a tuned threshold: the decay toward the sneak-walk
         * terminal is geometric, so anything below this means the brake has arrived at its
         * floor and further ticks buy nothing but a body that never leaves the ledge.
         */
        const val PIN_CONVERGENCE = 1e-4

        /**
         * Pinned ticks before convergence is judged: vanilla's sneaking-pose lag is one.
         *
         * Two, so the tick the pose arrives is still measured rather than being the tick
         * the decision is made on.
         */
        const val POSE_LAG_TICKS = 2

        /**
         * How fast the remembered step forgets, once the body stops advancing.
         *
         * A pinned body travels nothing at all, so the measured step collapses to zero and
         * would immediately claim the lip is out of reach. Decaying rather than replacing
         * keeps the reach honest while the clip is holding the body still.
         */
        const val STEP_DECAY = 0.9

        /**
         * Released frames that must pass before forward may be pressed again.
         *
         * Vanilla's double-tap window is seven ticks; waiting it out means the press can
         * only restart the window, never complete a tap into a sprint.
         */
        /**
         * Slack, in blocks, on "this tick would cross the lip".
         *
         * The clip fires off the body's own box rather than this projection, so the two do
         * not have to agree exactly -- this only has to press the key in time.
         */
        const val LIP_MARGIN = 0.1


        /**
         * Lateral error tolerated before the airborne trim strafes, in blocks.
         *
         * Under a third of the body's half width, so the correction starts well before any
         * part of it is hanging off the side of the pad it was aimed at.
         */
        const val LATERAL_DEADBAND = 0.08
    }
}
