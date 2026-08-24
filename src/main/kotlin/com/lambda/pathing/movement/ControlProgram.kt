/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.movement

import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState

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
