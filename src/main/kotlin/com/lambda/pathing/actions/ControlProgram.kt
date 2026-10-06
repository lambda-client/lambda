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

class LaunchTrigger(private val delayFrames: Int) {
	private var groundedTicks = 0

	var hasFired = false
		private set

	fun press(observed: MovementSimulationState): Boolean {
		if (hasFired || !observed.onGround) return false
		if (groundedTicks++ < delayFrames) return false
		hasFired = true
		return true
	}
}
