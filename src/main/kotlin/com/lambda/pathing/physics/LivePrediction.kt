package com.lambda.pathing.physics

class LivePrediction(
	private val simulator: MovementSimulator,
	private val inputProvider: MovementInputProvider,
) {
	val state: MovementSimulationState get() = simulator.state

	val tick: MovementSimulationTick
		get() = simulator.state.let {
			MovementSimulationTick(
				position = it.position,
				rotation = it.rotation,
				velocity = it.velocity,
				boundingBox = it.boundingBox,
				eyePos = it.position.add(0.0, simulator.eyeHeight, 0.0),
				onGround = it.onGround,
				isJumping = it.isJumping,
			)
		}

	fun advance(input: MovementSimulationInput = inputProvider.nextInput(simulator)): MovementSimulationTick {
		simulator.tickMovement(input)
		return tick
	}

	fun skipUntil(amount: Int = 20, until: (MovementSimulationTick) -> Boolean): MovementSimulationTick {
		repeat(amount) {
			val prediction = advance()
			if (until(prediction)) return prediction
		}
		return tick
	}
}
