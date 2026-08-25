package com.lambda.pathing.movement

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.launch.BounceSolution
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import kotlin.math.abs
import kotlin.math.hypot

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

    private fun approachForward(observed: MovementSimulationState): Double =
        if (observed.velocity.horizontalLength() >= solution.speed + solution.speedSlack) 0.0 else 1.0

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

        const val REBOUND_SPEED = 0.1

        const val LATERAL_DEADBAND = 0.05
    }
}
