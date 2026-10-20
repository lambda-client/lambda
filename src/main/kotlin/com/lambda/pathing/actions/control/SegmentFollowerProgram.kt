package com.lambda.pathing.actions.control

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.LaunchTrigger
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.core.horizontalDistance
import com.lambda.pathing.launch.AirPlan
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import kotlin.math.abs

private const val EASE_TURN_DEGREES = 50.0

private const val VIA_RELEASE_LEAD = 0.45

internal class SegmentFollowerProgram(
	nodes: List<HorizontalPoint>,
	private val sprint: Boolean,
	private val lookAheadNodes: Int,
	private val launch: LaunchTrigger?,
	private val maxYawChange: Double,
	private val easeTurns: Boolean = false,
	private val holdTicks: Int = Int.MAX_VALUE,
	private val airPlan: AirPlan? = null,
	private val holdJumpInFlight: Boolean = false,
	private var via: HorizontalPoint? = null,
) : ControlProgram {
	private var airborneTicks = 0
	private val pursuit = PursuitTracker(nodes)
	private val origin = nodes.first()

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		pursuit.advance(observed)
		val jump = launch?.press(observed) == true
		via = via?.takeIf { alongEdge(origin, it, observed.position.x, observed.position.z) < horizontalDistance(origin, it) - VIA_RELEASE_LEAD }

		val desiredYaw = yawTowards(via ?: pursuit.target(lookAheadNodes), observed)
		val yawError = Rotation.wrap(desiredYaw - observed.rotation.yaw)
		val yawDelta = yawError.coerceIn(-maxYawChange, maxYawChange)

		val flying = jump || (launch?.hasFired == true && !observed.onGround)
		val jumpKey = jump || (holdJumpInFlight && flying)
		if (flying) airborneTicks++ else airborneTicks = 0
		val coasting = flying && airborneTicks > holdTicks

		val steered = if (via == null && flying && !observed.onGround) airPlan?.steer(observed, observed.rotation.yaw + yawDelta, airborneTicks) else null

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
