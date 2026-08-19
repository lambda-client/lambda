/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState

/**
 * Releases everything and lets the body come to rest where it is.
 *
 * This is the tail that makes a *partial* plan publishable. A tape that simply stops
 * emitting inputs is not safe to execute: the body is still moving when the inputs run
 * out. A tape that ends in a certified stop is safe run to its end, whatever happens to
 * the planner afterwards — which is the invariant that lets the search publish committed
 * motion early and keep improving the rest while the body is already walking.
 *
 * It holds the facing rather than steering: braking is not the moment to introduce a turn,
 * and holding yaw keeps the stop deterministic for the executor to verify.
 */
internal class BrakeToStopProgram(private val heldYaw: Double) : ControlProgram {
    override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput =
        MovementSimulationInput(
            forward = 0.0,
            strafe = 0.0,
            sprint = false,
            jump = false,
            rotation = Rotation(heldYaw, observed.rotation.pitch),
        )
}
