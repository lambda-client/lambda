package com.lambda.pathing.movement

import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import kotlin.math.abs
import kotlin.math.hypot

/** Signed perpendicular distance of the body from the [takeoff]-[aim] line. */
internal fun lateralOffset(
    takeoff: HorizontalPoint,
    aim: HorizontalPoint,
    observed: MovementSimulationState,
): Double {
    val dx = aim.x - takeoff.x
    val dz = aim.z - takeoff.z
    val length = hypot(dx, dz)
    if (length <= 1e-9) return 0.0
    val px = observed.position.x - takeoff.x
    val pz = observed.position.z - takeoff.z
    return (dx * pz - dz * px) / length
}

/** Full strafe back toward the flight line once the offset leaves [deadband]; zero inside it. */
internal fun airborneStrafe(
    takeoff: HorizontalPoint,
    aim: HorizontalPoint,
    observed: MovementSimulationState,
    deadband: Double,
): Double {
    val offset = lateralOffset(takeoff, aim, observed)
    if (abs(offset) < deadband) return 0.0
    return if (offset > 0.0) 1.0 else -1.0
}
