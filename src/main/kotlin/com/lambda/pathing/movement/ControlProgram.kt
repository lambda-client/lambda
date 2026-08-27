package com.lambda.pathing.movement

import com.lambda.pathing.prediction.simulation.MovementSimulationInput
import com.lambda.pathing.prediction.simulation.MovementSimulationState

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
