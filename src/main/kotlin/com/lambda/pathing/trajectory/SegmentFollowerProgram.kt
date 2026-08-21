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
import kotlin.math.abs
import kotlin.math.hypot

internal class LaunchTrigger(private val delayFrames: Int) {
    private var groundedTicks = 0
    private var fired = false

    val hasFired: Boolean get() = fired

    fun press(observed: MovementSimulationState): Boolean {
        if (fired || !observed.onGround) return false
        if (groundedTicks++ < delayFrames) return false
        fired = true
        return true
    }
}

private const val EASE_TURN_DEGREES = 50.0

internal fun alongEdge(
    from: HorizontalPoint,
    to: HorizontalPoint,
    x: Double,
    z: Double,
): Double {
    val dx = to.x - from.x
    val dz = to.z - from.z
    val length = hypot(dx, dz)
    if (length <= 1e-9) return 0.0
    return ((x - from.x) * dx + (z - from.z) * dz) / length
}

internal class SegmentFollowerProgram(
    nodes: List<HorizontalPoint>,
    private val sprint: Boolean,
    private val lookAheadNodes: Int,
    private val launch: LaunchTrigger?,
    private val maxYawChange: Double,
    private val easeTurns: Boolean = false,
) : ControlProgram {
    private val pursuit = PursuitTracker(nodes)

    override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
        pursuit.advance(observed)
        val jump = launch?.press(observed) == true

        val desiredYaw = yawTowards(pursuit.target(lookAheadNodes), observed)
        val yawError = Rotation.wrap(desiredYaw - observed.rotation.yaw)
        val yawDelta = yawError.coerceIn(-maxYawChange, maxYawChange)

        return MovementSimulationInput(
            forward = if (easeTurns && abs(yawError) > EASE_TURN_DEGREES) 0.0 else 1.0,
            sprint = sprint,
            jump = jump,
            rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch),
        )
    }

}
