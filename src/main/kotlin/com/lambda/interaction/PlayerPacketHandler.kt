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
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.threading.runSafe
import com.lambda.util.collections.LimitedOrderedSet
import com.lambda.util.math.approximate
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround
import net.minecraft.util.math.Vec3d

object PlayerPacketHandler {
    val configurations = LimitedOrderedSet<PlayerPacketEvent.Pre>(100)

    var lastPosition: Vec3d = Vec3d.ZERO
    var lastRotation = Rotation.ZERO
    var lastSprint = false
    var lastOnGround = false
    var lastHorizontalCollision = false

    private var sendTicks = 0

    @JvmStatic
    fun sendPlayerPackets() {
        runSafe {
            PlayerPacketEvent.Pre(
                player.pos,
                RotationManager.activeRotation,
                player.isOnGround,
                player.isSprinting,
                player.horizontalCollision,
            ).post {
                updatePlayerPackets(this)
            }
        }
    }

    private fun SafeContext.updatePlayerPackets(new: PlayerPacketEvent.Pre) {
        configurations.add(new)

        reportSprint(lastSprint, new.isSprinting)

        if (mc.cameraEntity != player) return

        val position = new.position
        val (yaw, pitch) = new.rotation
        val onGround = new.onGround
        val isCollidingHorizontally = new.isCollidingHorizontally

        val updatePosition = position.approximate(lastPosition) || ++sendTicks >= 20
        val updateRotation = lastRotation.yaw != yaw || lastRotation.pitch != pitch

        when {
            updatePosition && updateRotation -> Full(position, yaw.toFloat(), pitch.toFloat(), onGround, isCollidingHorizontally)
            updatePosition -> PositionAndOnGround(position, onGround, isCollidingHorizontally)
            updateRotation -> LookAndOnGround(yaw.toFloat(), pitch.toFloat(), onGround, isCollidingHorizontally)
            lastOnGround != onGround || lastHorizontalCollision != isCollidingHorizontally -> OnGroundOnly(onGround, isCollidingHorizontally)
            else -> null
        }?.let {
            PlayerPacketEvent.Send(it).postChecked {
                connection.sendPacket(this.packet)

                if (updatePosition) {
                    lastPosition = position
                    sendTicks = 0
                }

                if (updateRotation) {
                    lastRotation = new.rotation
                }

                lastOnGround = onGround
                lastHorizontalCollision = isCollidingHorizontally
            }
        }

        // Update the server rotation in RotationManager
        RotationManager.onRotationSend()

        PlayerPacketEvent.Post().post()
    }

    fun SafeContext.reportSprint(previous: Boolean, new: Boolean) {
        if (previous == new) return

        val state = if (new) {
            ClientCommandC2SPacket.Mode.START_SPRINTING
        } else {
            ClientCommandC2SPacket.Mode.STOP_SPRINTING
        }

        connection.sendPacket(ClientCommandC2SPacket(player, state))
        lastSprint = new
    }
}


