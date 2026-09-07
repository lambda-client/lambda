package com.lambda.pathing.actions

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.launch.BounceSolution
import com.lambda.pathing.launch.BounceSolver
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState

internal class BounceProgram(
    private val takeoff: HorizontalPoint,
    private val aim: HorizontalPoint,
    private val solution: BounceSolution,
    private val maxYawChange: Double,
) : ControlProgram {
    private var airborne = false
    private var landed = false
    private var bounced = false
    private var flightTicks = 0

    override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {

        if (!observed.onGround) {
            airborne = true
            flightTicks++
        } else if (airborne) {
            if (observed.velocity.y > REBOUND_SPEED) bounced = true else landed = true
        }

        val desiredYaw = yawTowards(aim, observed)
        val yawDelta = yawStepTowards(desiredYaw, observed, maxYawChange)
        val rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch)

        var jump = false
        val forward: Double
        if (landed) {
            forward = 0.0
        } else if (airborne) {
            // A partial hold releases forward after the solved tick count; the rest
            // of the arc coasts, exactly as the solver flew it.
            forward = if (solution.holdForward && flightTicks < solution.holdTicks) 1.0 else 0.0
        } else if (standingStart) {
            // A standing launch: creep to the lip, come to rest, then launch from rest;
            // there is no entry speed to reproduce.
            val along = alongEdge(takeoff, aim, observed.position.x, observed.position.z)
            val speed = observed.velocity.horizontalLength()
            val runout = (speed + CREEP_TAP_SPEED) * COAST_RUNOUT_TICKS
            if (along + runout < solution.launchOffset) {
                forward = 1.0 // a tap from here still coasts to rest before the lip
            } else if (speed > solution.speedSlack && !launchedStanding) {
                forward = 0.0 // rolling out the last of the creep
            } else {
                // At rest on the lip: this tick is the model's launch tick.
                launchedStanding = true
                jump = solution.jump
                forward = if (solution.holdForward) 1.0 else 0.0
            }
        } else {
            // A moving-entry launch: regulate toward the solved speed, and press
            // jump on the last tick still standing on the lip -- the same
            // one-step-lookahead trigger RunUpLaunchProgram uses.
            if (solution.jump) {
                val along = alongEdge(takeoff, aim, observed.position.x, observed.position.z)
                val nextAlong = alongEdge(
                    takeoff, aim,
                    observed.position.x + observed.velocity.x,
                    observed.position.z + observed.velocity.z,
                )
                val reach = (nextAlong - along).coerceAtLeast(0.0)
                if (solution.launchOffset - along <= reach + LIP_MARGIN) jump = true
            }
            forward = when {
                jump -> if (solution.holdForward) 1.0 else 0.0
                else -> approachForward(observed)
            }
        }

        // A standing start creeps WITHOUT sprint (a sprint tap moves nearly a quarter
        // block, off the lip); the sprint-jump boost needs the flag ON THE LAUNCH TICK
        // only. See docs/decisions/launch-solver.md (standing starts).
        val sprint = solution.sprint && forward > 0.0 &&
            (!standingStart || launchedStanding || airborne)
        return MovementSimulationInput(
            forward = forward,
            strafe = if (airborne && !landed) airborneStrafe(takeoff, aim, observed, LATERAL_DEADBAND) else 0.0,
            sprint = sprint,
            jump = jump,
            sneak = false,
            rotation = rotation,
        )
    }

    private val standingStart get() = solution.speed <= BounceSolver.STANDING_REST_SPEED

    private var launchedStanding = false

    private fun approachForward(observed: MovementSimulationState): Double =
        if (observed.velocity.horizontalLength() >= solution.speed + solution.speedSlack) 0.0 else 1.0

    private companion object {

        const val REBOUND_SPEED = 0.1

        const val LATERAL_DEADBAND = 0.05

        const val LIP_MARGIN = LIP_LOOKAHEAD_MARGIN

        /** One walk tap's speed; with [COAST_RUNOUT_TICKS] it bounds a creep step's travel. */
        const val CREEP_TAP_SPEED = 0.06

        /** Ground friction geometric runout: 1 / (1 - 0.91 * 0.6). */
        const val COAST_RUNOUT_TICKS = 2.2
    }
}
