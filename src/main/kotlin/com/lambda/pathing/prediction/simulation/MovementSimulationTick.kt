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

package com.lambda.pathing.prediction.simulation

import com.lambda.interaction.managers.rotating.Rotation
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d

data class MovementSimulationTick(
	val position: Vec3d,
	val rotation: Rotation,
	val velocity: Vec3d,
	val boundingBox: Box,
	val eyePos: Vec3d,
	val onGround: Boolean,
	val isJumping: Boolean,
	val simulator: MovementSimulator,
) {
    fun skipUntil(amount: Int = 20, block: (MovementSimulationTick) -> Boolean) =
        skipUntil(amount, { _, _ -> null }, block)

    fun skipUntil(
        amount: Int = 20,
        inputProvider: (tick: Int, current: MovementSimulationTick) -> MovementSimulationInput?,
        block: (MovementSimulationTick) -> Boolean,
    ) = with(simulator) {
        repeat(amount) { tick ->
            val prediction = tickMovement(inputProvider(tick, lastTick))
            if (block(prediction)) return@with prediction
        }

        return@with lastTick
    }
}