package com.lambda.pathing.physics

import com.lambda.context.SafeContext

fun SafeContext.buildPlayerPrediction(): LivePrediction = LivePrediction(
	MovementSimulator(
		profile = PlayerPhysicsProfile.capture(player),
		environment = LiveSimulationEnvironment(player.entityWorld, player),
		initialState = MovementSimulationState.from(player),
	),
	MovementInputProvider.live(player),
)
