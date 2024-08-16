package com.lambda.interaction

import com.lambda.context.SafeContext
import com.lambda.core.Loadable
import com.lambda.event.EventFlow.post
import com.lambda.event.EventFlow.postChecked
import com.lambda.event.events.PlayerPacketEvent
import com.lambda.interaction.rotation.Rotation
import com.lambda.threading.runSafe
import com.lambda.util.collections.LimitedOrderedSet
import com.lambda.util.math.VecUtils.approximate
import com.lambda.util.player.MovementUtils.motionX
import com.lambda.util.player.MovementUtils.motionZ
import com.lambda.util.primitives.extension.component1
import com.lambda.util.primitives.extension.component2
import com.lambda.util.primitives.extension.component3
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.*
import net.minecraft.util.math.Vec3d

object PlayerPacketManager : Loadable {
    val configurations = LimitedOrderedSet<PlayerPacketEvent.Pre>(100)

    var lastPosition = Vec3d.ZERO
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
                    onGround
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
                Full(x, y, z, yaw, pitch, onGround)
            }

            updatePosition -> {
                PositionAndOnGround(x, y, z, onGround)
            }

            updateRotation -> {
                LookAndOnGround(yaw, pitch, onGround)
            }

            lastOnGround != onGround -> {
                OnGroundOnly(onGround)
            }

            else -> null
        }

        if (updatePosition) {
            sendTicks = 0
        }

        packet?.let {
            PlayerPacketEvent.Send(it).postChecked {
                connection.sendPacket(this.packet)

                if (updatePosition) {
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


