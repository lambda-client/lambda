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

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.util.player.MovementUtils.forward
import com.lambda.util.player.MovementUtils.jumping
import com.lambda.util.player.MovementUtils.sneaking
import com.lambda.util.player.MovementUtils.sprinting
import com.lambda.util.player.MovementUtils.strafe
import net.minecraft.client.input.Input

data class MovementSimulationInput(
	val forward: Double = 0.0,
	val strafe: Double = 0.0,
	val jump: Boolean = false,
	val sneak: Boolean = false,
	val sprint: Boolean = false,
	val useItemSlowdown: Boolean = false,
	val rotation: Rotation? = null,
	/** A planned block interaction for this tick; the simulator ignores it until world-changing actions land. */
	val interaction: Interaction? = null,
) {
    companion object {
        fun from(
	        input: Input,
	        rotation: Rotation? = null,
	        useItemSlowdown: Boolean = false,
        ) = MovementSimulationInput(
            forward = input.forward.toDouble(),
            strafe = input.strafe.toDouble(),
            jump = input.jumping,
            sneak = input.sneaking,
            sprint = input.sprinting,
            useItemSlowdown = useItemSlowdown,
            rotation = rotation,
        )
    }
}