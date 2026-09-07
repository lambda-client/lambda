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

import com.lambda.pathing.core.VoxelPos
import net.minecraft.util.math.Direction

/**
 * A block interaction the body performs on one tick, alongside its movement input.
 *
 * Carried on [MovementSimulationInput] so a tape can express "use the door at P" or
 * "attack the block at P" at a frame. Nothing produces or consumes it yet; the slot
 * exists so the world-changing action families do not have to widen the input later.
 */
sealed interface Interaction {
    val target: VoxelPos

    /** Right-click the block face (open a door, place against it). */
    data class Use(override val target: VoxelPos, val face: Direction) : Interaction

    /** Left-click the block face (start or continue breaking it). */
    data class Attack(override val target: VoxelPos, val face: Direction) : Interaction
}
