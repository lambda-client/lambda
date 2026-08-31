package com.lambda.pathing.movement

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.prediction.simulation.MovementSimulationInput
import com.lambda.pathing.prediction.simulation.MovementSimulationState

internal class ClimbProgram(
    private val targetYaw: Double?,
    private val holdForward: Boolean,
    private val holdWhileClimbing: Boolean,
    private val climbWithJump: Boolean,
    private val maxYawChange: Double,
    private val takeoff: HorizontalPoint? = null,
    private val aim: HorizontalPoint? = null,
) : ControlProgram {

    /** Entering the column over a lip from above: descend, never rise. */
    private val overLip = holdForward && !holdWhileClimbing && !climbWithJump

    override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
        val yawDelta = targetYaw?.let { target ->
            Rotation.wrap(target - observed.rotation.yaw).coerceIn(-maxYawChange, maxYawChange)
        } ?: 0.0

        // Face the column before moving: pressing forward mid-turn walks an arc, and
        // on a one-block pillar top that arc leaves the block on the wrong side
        // before the yaw ever reaches the ladder.
        val aligned = targetYaw == null ||
            kotlin.math.abs(Rotation.wrap(targetYaw - observed.rotation.yaw)) <= ALIGNMENT_DEGREES

        val forward = if (observed.onGround) {
            if (overLip && !aligned) {
                0.0
            } else if (overLip && takeoff != null && aim != null) {
                // Creep off the lip: a full-speed walk-off carries ~0.2 of drift
                // into the column, and a ladder caps the FALL but barely brakes the
                // horizontal -- the body coasted clean through a one-wide column
                // and out the far side (measured on the field course's top entry).
                // Entering at tap speed keeps the total drift inside the cell.
                val along = alongEdge(takeoff, aim, observed.position.x, observed.position.z)
                val speed = observed.velocity.horizontalLength()
                when {
                    along + (speed + CREEP_TAP_SPEED) * COAST_RUNOUT_TICKS < LIP_ALONG -> 1.0
                    speed > CREEP_REST_SPEED -> 0.0
                    else -> 1.0 // at rest against the lip: one gentle tap over it
                }
            } else if (holdForward) 1.0 else 0.0
        } else if (holdWhileClimbing) {
            1.0
        } else if (overLip && observed.velocity.horizontalLength() > DRIFT_REST_SPEED) {
            // Brake the entry drift inside the column: a ladder caps the fall but
            // not the horizontal, and even a tap's worth of entry speed integrates
            // to a block of drift over a long descent -- out the far side.
            -1.0
        } else {
            0.0
        }

        return MovementSimulationInput(
            forward = forward,
            sprint = false,
            // Jump from the ground too: a ladder whose bottom cell hangs over a gap
            // must be ENTERED jumping -- walking in first drops the feet below the
            // ladder's bottom cell, vanilla stops counting the body as climbing the
            // moment the feet cell is not climbable, and nothing arrests the fall
            // (measured as the body pacing in front of the field course's ladder).
            // On a floored ladder base the extra hop costs a frame or two and the
            // wall-press takes over identically.
            jump = climbWithJump,
            rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch),
        )
    }

    private companion object {
        /** See BounceProgram's creep: walk-tap speed and ground-friction runout. */
        const val CREEP_TAP_SPEED = 0.06
        const val COAST_RUNOUT_TICKS = 2.2
        const val CREEP_REST_SPEED = 0.03

        /** The lip: half a cell to the boundary plus the body's half-width. */
        const val LIP_ALONG = 0.8

        /** Residual drift the in-column brake tolerates (braking below it risks
         *  pressing the far wall, which vanilla answers with a climb UP). */
        const val DRIFT_REST_SPEED = 0.02

        /** Close enough to the column's bearing to start the creep. */
        const val ALIGNMENT_DEGREES = 25.0
    }
}
