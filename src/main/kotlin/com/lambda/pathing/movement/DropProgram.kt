package com.lambda.pathing.movement

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import kotlin.math.abs
import kotlin.math.hypot

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

            aligned && !solution.holdForward -> 0.0

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

    private fun pinningToLip(observed: MovementSimulationState): Boolean {
        if (aligned) return false
        if (!atLip(observed)) return false

        val speed = observed.velocity.horizontalLength()
        if (speed <= solution.speed + SPEED_TOLERANCE) {
            aligned = true
            return false
        }

        if (pinnedTicks >= POSE_LAG_TICKS && speed > slowestPinnedSpeed - PIN_CONVERGENCE) {
            aligned = true
            return false
        }

        pinnedTicks++
        slowestPinnedSpeed = minOf(slowestPinnedSpeed, speed)
        return true
    }

    private fun atLip(observed: MovementSimulationState): Boolean {
        val along = alongEdge(takeoff, aim, observed.position.x, observed.position.z)
        val step = if (previousAlong.isNaN()) 0.0 else along - previousAlong
        previousAlong = along
        previousStep = maxOf(step, previousStep * STEP_DECAY)

        val reach = maxOf(previousStep, observed.velocity.horizontalLength())
        return -along <= reach + LIP_MARGIN
    }

    private fun airborneForward(observed: MovementSimulationState): Double {
        if (!solution.holdForward) return 0.0
        val travelled = alongEdge(takeoff, aim, observed.position.x, observed.position.z)
        return if (travelled < flightLength) 1.0 else 0.0
    }

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

        const val SPEED_TOLERANCE = 0.015

        const val PIN_CONVERGENCE = 1e-4

        const val POSE_LAG_TICKS = 2

        const val STEP_DECAY = 0.9

        const val LIP_MARGIN = 0.1

        const val LATERAL_DEADBAND = 0.08
    }
}
