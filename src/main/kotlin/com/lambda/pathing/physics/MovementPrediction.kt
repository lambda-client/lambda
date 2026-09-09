package com.lambda.pathing.physics

import com.lambda.context.SafeContext

fun SafeContext.buildPlayerPrediction(): LivePrediction =
	LivePrediction(buildMovementSimulator(), MovementInputProvider.live(player))

fun SafeContext.buildMovementSimulator(
	initialState: MovementSimulationState = MovementSimulationState.from(player),
): MovementSimulator = MovementSimulator(
	profile = PlayerPhysicsProfile.capture(player),
	environment = LiveSimulationEnvironment(player.entityWorld, player, entityCollisions = true),
	initialState = initialState,
)
