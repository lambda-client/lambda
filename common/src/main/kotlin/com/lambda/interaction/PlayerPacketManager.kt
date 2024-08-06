package com.lambda.interaction

import com.lambda.context.SafeContext
import com.lambda.core.Loadable
import com.lambda.event.EventFlow.post
import com.lambda.event.EventFlow.postChecked
import com.lambda.event.events.PlayerPacketEvent
import com.lambda.interaction.rotation.Rotation.Companion.fixSensitivity
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

object PlayerPacketManager : Loadable {
    val configurations = LimitedOrderedSet<PlayerPacketEvent.Pre>(100)
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
        val previous = configurations.lastOrNull() ?: new
        configurations.add(new)

        reportSprint(previous, new)
        reportSneak(previous, new)

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

        val updatePosition = position.approximate(previous.position, 2.0E-4) || ++sendTicks >= 20
        // has to be different in float precision
        val updateRotation = !rotation.equalFloat(previous.rotation)

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

            previous.onGround != onGround -> {
                OnGroundOnly(onGround)
            }

            else -> null
        }

        if (updatePosition) {
            sendTicks = 0
        }

        packet?.let {
            PlayerPacketEvent.Post(it).postChecked {
                connection.sendPacket(this.packet)
            }
        }
    }

    private fun SafeContext.reportSprint(previous: PlayerPacketEvent.Pre, new: PlayerPacketEvent.Pre) {
        if (previous.isSprinting == new.isSprinting) return

        val state = if (new.isSprinting) {
            ClientCommandC2SPacket.Mode.START_SPRINTING
        } else {
            ClientCommandC2SPacket.Mode.STOP_SPRINTING
        }

        connection.sendPacket(ClientCommandC2SPacket(player, state))
    }

    private fun SafeContext.reportSneak(previous: PlayerPacketEvent.Pre, new: PlayerPacketEvent.Pre) {
        if (previous.isSneaking == new.isSneaking) return

        val state = if (new.isSneaking) {
            ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY
        } else {
            ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY
        }

        connection.sendPacket(ClientCommandC2SPacket(player, state))
    }
}


