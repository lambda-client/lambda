package com.lambda.pathing.actions.control

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.core.DEGENERATE_LENGTH
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.physics.MovementSimulationState
import kotlin.math.abs
import kotlin.math.hypot

internal const val LIP_LOOKAHEAD_MARGIN = 0.05

internal const val CREEP_TAP_SPEED = 0.06

internal val GROUND_COAST_PER_SPEED = BallisticProfile.VANILLA.coastDistancePerSpeed

internal fun lateralOffset(
	takeoff: HorizontalPoint,
	aim: HorizontalPoint,
	observed: MovementSimulationState,
): Double {
	val dx = aim.x - takeoff.x
	val dz = aim.z - takeoff.z
	val length = hypot(dx, dz)
	if (length <= DEGENERATE_LENGTH) return 0.0
	val px = observed.position.x - takeoff.x
	val pz = observed.position.z - takeoff.z
	return (dx * pz - dz * px) / length
}

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

internal fun rotationToward(aim: HorizontalPoint, observed: MovementSimulationState, maxYawChange: Double): Rotation {
	val yawDelta = yawStepTowards(yawTowards(aim, observed), observed, maxYawChange)
	return Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch)
}

internal fun reachesLip(
	takeoff: HorizontalPoint,
	aim: HorizontalPoint,
	observed: MovementSimulationState,
	along: Double,
	lipAlong: Double,
): Boolean {
	val nextAlong = alongEdge(
		takeoff, aim,
		observed.position.x + observed.velocity.x,
		observed.position.z + observed.velocity.z,
	)
	val reach = (nextAlong - along).coerceAtLeast(0.0)
	return lipAlong - along <= reach + LIP_LOOKAHEAD_MARGIN
}
