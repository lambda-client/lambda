package com.lambda.pathing.movement

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import kotlin.math.abs
import kotlin.math.hypot

internal class RunUpLaunchProgram(
    private val takeoff: HorizontalPoint,
    private val aim: HorizontalPoint,
    private val solution: LaunchSolution,
    retreatAlong: Double,
    hopAlong: Double?,
    private val maxYawChange: Double,
) : ControlProgram {
    private val retreatAlong = retreatAlong
    private val jumpTargets = ArrayDeque(listOfNotNull(hopAlong, solution.launchOffset))

    private var retreatDone = false
    private var launched = false
    private var landedAfterLaunch = false

    override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
        val along = alongEdge(takeoff, aim, observed.position.x, observed.position.z)
        if (launched && observed.onGround && along > solution.launchOffset) landedAfterLaunch = true

        val desiredYaw = yawTowards(aim, observed)
        val yawDelta = yawStepTowards(desiredYaw, observed, maxYawChange)
        val rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch)

        if (landedAfterLaunch) return MovementSimulationInput(rotation = rotation)
        if (launched && !observed.onGround) {
            return MovementSimulationInput(
                forward = if (solution.holdForward) 1.0 else 0.0,
                strafe = airborneStrafe(observed),
                sprint = solution.sprint && solution.holdForward,
                rotation = rotation,
            )
        }

        if (!retreatDone) {
            if (along > retreatAlong && observed.onGround) {
                return MovementSimulationInput(forward = -1.0, rotation = rotation)
            }
            retreatDone = true
        }

        var jump = false
        if (observed.onGround && jumpTargets.isNotEmpty()) {
            val nextAlong = alongEdge(
                takeoff, aim,
                observed.position.x + observed.velocity.x,
                observed.position.z + observed.velocity.z,
            )
            val reach = (nextAlong - along).coerceAtLeast(0.0)
            if (jumpTargets.first() - along <= reach + LIP_MARGIN) {
                jump = true
                if (jumpTargets.size == 1) launched = true
                jumpTargets.removeFirst()
            }
        }
        return MovementSimulationInput(
            forward = 1.0,
            sprint = solution.sprint,
            jump = jump,
            rotation = rotation,
        )
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
        const val LIP_MARGIN = 0.05

        const val LATERAL_DEADBAND = 0.08
    }
}
