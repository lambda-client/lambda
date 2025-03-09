/*
 * Copyright 2024 Lambda
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
import com.lambda.interaction.request.rotation.Rotation
import com.lambda.interaction.request.rotation.RotationManager
import com.lambda.threading.runSafe
import com.lambda.util.collections.LimitedOrderedSet
import com.lambda.util.math.approximate
import com.lambda.util.math.component1
import com.lambda.util.math.component2
import com.lambda.util.math.component3
import com.lambda.util.player.MovementUtils.motionX
import com.lambda.util.player.MovementUtils.motionZ
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.*
import net.minecraft.util.math.Vec3d

object PlayerPacketManager {
    val configurations = LimitedOrderedSet<PlayerPacketEvent.Pre>(100)

    var lastPosition: Vec3d = Vec3d.ZERO
    var lastRotation = Rotation.ZERO
    var lastSprint = false
    var lastSneak = false
    var lastOnGround = false

    private var sendTicks = 0

    @JvmStatic
    fun sendPlayerPackets() {
        runSafe {
            PlayerPacketEvent.Pre(
                player.pos,
                RotationManager.currentRotation,
                player.isOnGround,
                player.isSprinting,
                player.isSneaking
            ).post {
                updatePlayerPackets(this)
            }
        }
    }

    private fun SafeContext.updatePlayerPackets(new: PlayerPacketEvent.Pre) {
        configurations.add(new)

        reportSprint(lastSprint, new.isSprinting)
        reportSneak(lastSneak, new.isSneaking)

        if (mc.cameraEntity != player) return

        val rotation = new.rotation
        val position = new.position
        val (yaw, pitch) = rotation.float
        val onGround = new.onGround

        if (player.hasVehicle()) {
            connection.sendPacket(
                Full(
                    player.motionX,
                    -999.0,
                    player.motionZ,
                    yaw,
                    pitch,
                    onGround,
                    true // TODO: Check this after update
                )
            )
            return
        }

        val updatePosition = position.approximate(lastPosition, 2.0E-4) || ++sendTicks >= 20
        // has to be different in float precision
        val updateRotation = !rotation.equalFloat(lastRotation)

        val (x, y, z) = position

        val packet = when {
            updatePosition && updateRotation -> {
                Full(x, y, z, yaw, pitch, onGround, true) // TODO: Check this after update
            }

            updatePosition -> {
                PositionAndOnGround(x, y, z, onGround, true) // TODO: Check this after update
            }

            updateRotation -> {
                LookAndOnGround(yaw, pitch, onGround, true) // TODO: Check this after update
            }

            lastOnGround != onGround -> {
                OnGroundOnly(onGround, true) // TODO: Check this after update
            }

            else -> null
        }

        packet?.let {
            PlayerPacketEvent.Send(it).postChecked {
                connection.sendPacket(this.packet)

                if (updatePosition) {
                    sendTicks = 0
                    lastPosition = position
                }

                if (updateRotation) {
                    lastRotation = rotation
                }

                lastOnGround = onGround
            }
        }

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

    fun SafeContext.reportSneak(previous: Boolean, new: Boolean) {
        if (previous == new) return

        val state = if (new) {
            ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY
        } else {
            ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY
        }

        connection.sendPacket(ClientCommandC2SPacket(player, state))
        lastSneak = new
    }
}


