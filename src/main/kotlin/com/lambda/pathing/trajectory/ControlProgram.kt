/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState

/**
 * The one control contract shared by simulation and eventual live execution.
 * A controller may inspect the state observed at the start of [frame], but the
 * executor is not allowed to reinterpret its output.
 */
fun interface ControlProgram {
    fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput
}

/** First implementation: an immutable sequence of already chosen inputs. */
class InputTape(inputs: Iterable<MovementSimulationInput>) : ControlProgram {
    private val inputs = inputs.toList()

    val frameCount: Int get() = inputs.size

    override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput =
        inputs[frame]

    operator fun get(frame: Int): MovementSimulationInput = inputs[frame]

    fun asList(): List<MovementSimulationInput> = inputs
}
