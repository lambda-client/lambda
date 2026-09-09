package com.lambda.pathing.physics

sealed interface MovementSimulationStepResult {
	data class Advanced(val state: MovementSimulationState) : MovementSimulationStepResult
	data class Rejected(val failure: SimulationEnvironmentException) : MovementSimulationStepResult
}
