package com.lambda.pathing.movement

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import kotlin.math.abs
import kotlin.math.hypot

class LaunchTrigger(private val delayFrames: Int) {
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
