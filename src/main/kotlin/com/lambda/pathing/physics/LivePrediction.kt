package com.lambda.pathing.physics

class LivePrediction(
	private val simulator: MovementSimulator,
	private val inputProvider: MovementInputProvider,
) {
	val state: MovementSimulationState get() = simulator.state

	fun advance(input: MovementSimulationInput = inputProvider.nextInput(simulator)): MovementSimulationState =
		simulator.tickMovement(input)

	fun skipUntil(amount: Int, until: (MovementSimulationState) -> Boolean): MovementSimulationState {
		repeat(amount) {
			val prediction = advance()
			if (until(prediction)) return prediction
		}
		return state
	}
}
