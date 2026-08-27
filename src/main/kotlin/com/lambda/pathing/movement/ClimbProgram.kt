package com.lambda.pathing.movement

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.prediction.simulation.MovementSimulationInput
import com.lambda.pathing.prediction.simulation.MovementSimulationState

internal class ClimbProgram(
    private val targetYaw: Double?,
    private val holdForward: Boolean,
    private val holdWhileClimbing: Boolean,
    private val climbWithJump: Boolean,
    private val maxYawChange: Double,
) : ControlProgram {
    override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
        val yawDelta = targetYaw?.let { target ->
            Rotation.wrap(target - observed.rotation.yaw).coerceIn(-maxYawChange, maxYawChange)
        } ?: 0.0
        return MovementSimulationInput(

            forward = if (if (observed.onGround) holdForward else holdWhileClimbing) 1.0 else 0.0,
            sprint = false,
            jump = climbWithJump && (!holdForward || !observed.onGround),
            rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch),
        )
    }
}
