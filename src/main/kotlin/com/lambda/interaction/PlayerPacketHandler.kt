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

package com.lambda.interaction

import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.post
import com.lambda.event.EventFlow.postChecked
import com.lambda.event.events.PlayerPacketEvent
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.threading.runSafe
import com.lambda.util.collections.LimitedOrderedSet
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround

object PlayerPacketHandler {
    val configurations = LimitedOrderedSet<PlayerPacketEvent.Pre>(100)

    @JvmStatic
    fun sendPlayerPackets(packet: Packet<*>) {
        runSafe {
            PlayerPacketEvent.Pre(
                player.pos,
                RotationManager.activeRotation,
                player.isOnGround,
                player.isSprinting,
                player.horizontalCollision,
            ).post {
                updatePlayerPackets(this, packet)
            }
        }
    }

    private fun SafeContext.updatePlayerPackets(new: PlayerPacketEvent.Pre, packet: Packet<*>) {
        configurations.add(new)

        val (yaw, pitch) = new.rotation

        when (packet) {
            is Full -> Full(new.position, yaw.toFloat(), pitch.toFloat(), new.onGround, new.isCollidingHorizontally)
            is PositionAndOnGround -> PositionAndOnGround(new.position, new.onGround, new.isCollidingHorizontally)
            is LookAndOnGround -> LookAndOnGround(yaw.toFloat(), pitch.toFloat(), new.onGround, new.isCollidingHorizontally)
            is OnGroundOnly -> OnGroundOnly(new.onGround, new.isCollidingHorizontally)
            else -> null
        }?.let {
            PlayerPacketEvent.Send(it).postChecked {
                connection.sendPacket(this.packet)
            }
        }

        PlayerPacketEvent.Post().post()
    }
}


