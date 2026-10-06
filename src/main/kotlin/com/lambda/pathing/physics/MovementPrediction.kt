package com.lambda.pathing.physics

import com.lambda.context.SafeContext
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.util.player.MovementUtils.forward
import com.lambda.util.player.MovementUtils.jumping
import com.lambda.util.player.MovementUtils.moveYaw
import com.lambda.util.player.MovementUtils.sneaking
import com.lambda.util.player.MovementUtils.strafe

fun SafeContext.predictPlayerUntil(ticks: Int, until: (MovementSimulationState) -> Boolean): MovementSimulationState {
	val simulator = MovementSimulator(
		profile = PlayerPhysicsProfile.capture(player),
		environment = LiveSimulationEnvironment(player.entityWorld, player),
		initialState = MovementSimulationState.from(player),
	)
	val input = MovementSimulationInput(
		forward = player.input.forward.toDouble(),
		strafe = player.input.strafe.toDouble(),
		jump = player.input.jumping,
		sneak = player.input.sneaking,
		sprint = player.isSprinting,
		useItemSlowdown = player.isUsingItem,
		rotation = Rotation(player.moveYaw, player.pitch),
	)
	repeat(ticks) {
		val state = simulator.tickMovement(input)
		if (until(state)) return state
	}
	return simulator.state
}
