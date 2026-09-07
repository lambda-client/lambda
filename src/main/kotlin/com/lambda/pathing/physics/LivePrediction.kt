/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.pathing.physics

import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.MovementSimulationTick
import com.lambda.pathing.physics.MovementSimulator
import net.minecraft.util.math.Vec3d

/**
 * Drives a [MovementSimulator] from an input source, tick by tick, for live prediction
 * (fall damage, next-tick position). The planner never uses this; it rolls tapes through
 * [MovementSimulator.stepFrom] directly.
 */
class LivePrediction(
    private val simulator: MovementSimulator,
    private val inputProvider: MovementInputProvider,
) {
    val state: MovementSimulationState get() = simulator.state

    val tick: MovementSimulationTick get() = simulator.state.let {
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

    /** Advances up to [amount] ticks, returning the first tick that satisfies [until], else the last. */
    fun skipUntil(amount: Int = 20, until: (MovementSimulationTick) -> Boolean): MovementSimulationTick {
        repeat(amount) {
            val prediction = advance()
            if (until(prediction)) return prediction
        }
        return tick
    }
}
