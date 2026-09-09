package com.lambda.pathing.actions.control

import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.LaunchTrigger

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.launch.AirSteering
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import kotlin.math.abs


private const val EASE_TURN_DEGREES = 50.0

internal class SegmentFollowerProgram(
	nodes: List<HorizontalPoint>,
	private val sprint: Boolean,
	private val lookAheadNodes: Int,
	private val launch: LaunchTrigger?,
	private val maxYawChange: Double,
	private val easeTurns: Boolean = false,

	private val holdForwardInFlight: Boolean = true,

	private val holdTicks: Int = Int.MAX_VALUE,

	private val airPlan: AirSteering.AirPlan? = null,

	private val holdJumpInFlight: Boolean = false,
) : ControlProgram {
	private var airborneTicks = 0
	private val pursuit = PursuitTracker(nodes)

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		pursuit.advance(observed)
		val jump = launch?.press(observed) == true

		val desiredYaw = yawTowards(pursuit.target(lookAheadNodes), observed)
		val yawError = Rotation.wrap(desiredYaw - observed.rotation.yaw)
		val yawDelta = yawError.coerceIn(-maxYawChange, maxYawChange)

		val flying = jump || (launch?.hasFired == true && !observed.onGround)
		val jumpKey = jump || (holdJumpInFlight && flying)
		if (flying) airborneTicks++ else airborneTicks = 0
		val coasting = flying && (!holdForwardInFlight || airborneTicks > holdTicks)

		val steered = if (flying && !observed.onGround && airPlan != null) {
			AirSteering.steer(
				positionX = observed.position.x,
				positionZ = observed.position.z,
				velocityX = observed.velocity.x,
				velocityZ = observed.velocity.z,
				yawDegrees = observed.rotation.yaw + yawDelta,
				aimX = airPlan.aimX,
				aimZ = airPlan.aimZ,
				unitX = airPlan.unitX,
				unitZ = airPlan.unitZ,
				remainingTicks = airPlan.airTicks - airborneTicks + 2,
				plannedHoldTicks = when {
					!holdForwardInFlight -> 0
					else -> (airPlan.holdTicks - airborneTicks + 1).coerceAtLeast(0)
				},
				sprintAcceleration = airPlan.sprintAcceleration,
				walkAcceleration = airPlan.walkAcceleration,
			)
		} else null

		return MovementSimulationInput(
			forward = when {
				steered != null -> steered.forward
				coasting -> 0.0
				easeTurns && abs(yawError) > EASE_TURN_DEGREES -> 0.0
				else -> 1.0
			},
			strafe = steered?.strafe ?: 0.0,
			sprint = sprint,
			jump = jumpKey,
			rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch),
		)
	}

}
