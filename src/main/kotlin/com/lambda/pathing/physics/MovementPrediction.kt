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

import com.lambda.context.SafeContext
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.LiveSimulationEnvironment
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.physics.MovementSimulator

/**
 * Builds the player movement prediction engine based on minecraft physics logic.
 *
 * The simulator is input-driven; this helper is the "predict the current player with
 * current live input" entry point.
 *
 * Not implemented:
 * - elytra movement
 * - movement in fluids
 * - ladder climbing
 * - movement in webs
 * - item-specific movement slowdown
 */
fun SafeContext.buildPlayerPrediction(): LivePrediction =
    LivePrediction(buildMovementSimulator(), MovementInputProvider.live(player))

fun SafeContext.buildMovementSimulator(
    initialState: MovementSimulationState = MovementSimulationState.from(player),
): MovementSimulator = MovementSimulator(
    profile = PlayerPhysicsProfile.capture(player),
    environment = LiveSimulationEnvironment(player.entityWorld, player, entityCollisions = true),
    initialState = initialState,
)
