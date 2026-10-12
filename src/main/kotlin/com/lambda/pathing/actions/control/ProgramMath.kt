package com.lambda.pathing.actions.control

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.core.DEGENERATE_LENGTH
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

internal const val LIP_LOOKAHEAD_MARGIN = 0.05

internal const val CREEP_TAP_SPEED = 0.06

internal val GROUND_COAST_PER_SPEED = BallisticProfile.VANILLA.coastDistancePerSpeed

private const val AIRBORNE_STRAFE_DEADBAND = 0.08

private const val HOVER_CENTERING = 0.08

private const val HOVER_COAST_TICKS = 5.0

private const val HOVER_INPUT = 0.3

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
): Double {
	val offset = lateralOffset(takeoff, aim, observed)
	if (abs(offset) < AIRBORNE_STRAFE_DEADBAND) return 0.0
	return if (offset > 0.0) 1.0 else -1.0
}

internal fun yawTowards(target: HorizontalPoint, observed: MovementSimulationState): Double =
	Math.toDegrees(atan2(target.z - observed.position.z, target.x - observed.position.x)) - 90.0

internal fun yawStepTowards(desiredYaw: Double, observed: MovementSimulationState, maxYawChange: Double): Double =
	Rotation.wrap(desiredYaw - observed.rotation.yaw).coerceIn(-maxYawChange, maxYawChange)

internal fun pitchTowards(desiredPitch: Double, observed: MovementSimulationState, maxChange: Double): Double =
	observed.rotation.pitch + (desiredPitch - observed.rotation.pitch).coerceIn(-maxChange, maxChange)

internal fun brakeToStop(heldYaw: Double) =
	ControlProgram { _, observed -> MovementSimulationInput(rotation = Rotation(heldYaw, observed.rotation.pitch)) }

internal fun treadWater(heldYaw: Double) =
	ControlProgram { _, observed -> MovementSimulationInput(jump = observed.touchingWater, rotation = Rotation(heldYaw, observed.rotation.pitch)) }

internal fun hover(center: HorizontalPoint, depth: Double, maxYawChange: Double) = ControlProgram { _, observed ->
	val jump = observed.position.y + observed.velocity.y * HOVER_COAST_TICKS < depth
	val offsetX = center.x - (observed.position.x + observed.velocity.x * HOVER_COAST_TICKS)
	val offsetZ = center.z - (observed.position.z + observed.velocity.z * HOVER_COAST_TICKS)
	val level = Rotation(observed.rotation.yaw, 0.0)
	if (observed.swimming || hypot(offsetX, offsetZ) <= HOVER_CENTERING) {
		MovementSimulationInput(jump = jump, rotation = level)
	} else {
		val aim = HorizontalPoint(observed.position.x + offsetX, observed.position.y, observed.position.z + offsetZ)
		MovementSimulationInput(forward = HOVER_INPUT, jump = jump, rotation = Rotation(rotationToward(aim, observed, maxYawChange).yaw, 0.0))
	}
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
