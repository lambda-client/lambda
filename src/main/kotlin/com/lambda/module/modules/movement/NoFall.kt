/*
 * Copyright 2025 Lambda
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

package com.lambda.module.modules.movement

import com.lambda.event.events.MovementEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.component1
import com.lambda.util.math.component2
import com.lambda.util.math.component3
import com.lambda.util.player.MovementUtils.motion
import com.lambda.util.player.MovementUtils.motionY
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

object NoFall : Module(
	name = "NoFall",
	description = "Reduces fall damage",
	tag = ModuleTag.MOVEMENT,
) {
	private val mode by setting("Mode", Mode.Grim)

	enum class Mode {
		Grim
	}

	init {
		listen<MovementEvent.Player.Post> {
			when (mode) {
				Mode.Grim -> {
					if (player.fallDistance + player.motionY < 3.0) return@listen

					val (x, y, z) = player.pos
					connection.sendPacket(PlayerMoveC2SPacket.Full(x, y + 0.0000000001, z, 0.01f, 90f, false, true)) // TODO: Check this after update
					connection.sendPacket(PlayerInteractItemC2SPacket(Hand.OFF_HAND, 0, player.yaw, player.pitch)) // TODO: This is wrong, fix it
					connection.sendPacket(
						PlayerActionC2SPacket(
							PlayerActionC2SPacket.Action.RELEASE_USE_ITEM,
							BlockPos.ORIGIN,
							Direction.DOWN
						)
					)
					player.motion = Vec3d.ZERO

					player.fallDistance = 0.0
				}
			}
		}
	}

}
