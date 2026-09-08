package com.lambda.pathing.actions

import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState

fun interface ControlProgram {
	fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput
}

class InputTape(inputs: Iterable<MovementSimulationInput>) : ControlProgram {
	private val inputs = inputs.toList()

	val frameCount: Int get() = inputs.size

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput =
		inputs[frame]

	operator fun get(frame: Int): MovementSimulationInput = inputs[frame]

	fun asList(): List<MovementSimulationInput> = inputs
}
