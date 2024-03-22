package com.lambda.manager

import com.lambda.Loadable
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow
import com.lambda.event.events.PlayerPacketEvent
import com.lambda.interaction.rotation.Rotation
import com.lambda.threading.runSafe
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.player.MovementUtils.motionX
import com.lambda.util.player.MovementUtils.motionZ
import com.lambda.util.primitives.extension.component1
import com.lambda.util.primitives.extension.component2
import com.lambda.util.primitives.extension.component3
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.*
import net.minecraft.util.math.MathHelper.square
import net.minecraft.util.math.Vec3d

object PlayerPacketManager : Loadable {
    private var prevPosition: Vec3d = Vec3d.ZERO
    private var lastPosition = Vec3d(0.0, -1000.0, 0.0)
    private var lastRotation = Rotation(0.0, -10000.0)
    private var lastOnGround: Boolean? = null
    private var lastSprinting: Boolean? = null
    private var lastSneaking: Boolean? = null
    private var sendTicks = 0

    @JvmStatic
    fun sendPlayerPackets() {
        runSafe {
            EventFlow.post(PlayerPacketEvent.Pre(
                player.pos,
                RotationManager.currentRotation,
                player.isOnGround,
                player.isSprinting
            )) {
                updatePlayerPackets(this)
            }
        }
    }

    private fun SafeContext.updatePlayerPackets(event: PlayerPacketEvent.Pre) {
        reportSprint(event.isSprinting)
        reportSneak(player.isSneaking)

        if (mc.cameraEntity != player) return

        val position = event.position
        val rotation = event.rotation
        val ground = event.onGround

        RotationManager.currentRotation = rotation

        if (player.hasVehicle()) {
            connection.sendPacket(Full(
                player.motionX,
                -999.0,
                player.motionZ,
                rotation.yaw.toFloat(),
                rotation.pitch.toFloat(),
                ground
            ))
            lastRotation = rotation
            return
        }

        val updatePosition = (position.subtract(lastPosition) distSq Vec3d.ZERO) > square(2.0E-4) || ++sendTicks >= 20
        val updateRotation = rotation != lastRotation

        val (x, y, z) = position

        val (yawD, pitchD) = rotation
        val (yaw, pitch) = yawD.toFloat() to pitchD.toFloat()

        val packet = when {
            updatePosition && updateRotation -> {
                Full(x, y, z, yaw, pitch, ground)
            }
            updatePosition -> {
                PositionAndOnGround(x, y, z, ground)
            }
            updateRotation -> {
                LookAndOnGround(yaw, pitch, ground)
            }
            lastOnGround != ground -> {
                OnGroundOnly(ground)
            }
            else -> null
        }

        if (updatePosition) {
            prevPosition = lastPosition
            lastPosition = position
            sendTicks = 0
        }
        if (updateRotation) {
            lastRotation = rotation
        }

        lastOnGround = ground

        packet?.let {
            EventFlow.postChecked(PlayerPacketEvent.Post(it)) {
                connection.sendPacket(this.packet)
            }
        }
    }

    private fun SafeContext.reportSprint(isSprinting: Boolean) {
        if (lastSprinting == isSprinting) return
        lastSprinting = isSprinting

        val state = if (isSprinting) {
            ClientCommandC2SPacket.Mode.START_SPRINTING
        } else {
            ClientCommandC2SPacket.Mode.STOP_SPRINTING
        }

        connection.sendPacket(ClientCommandC2SPacket(player, state))
    }

    private fun SafeContext.reportSneak(flag: Boolean) {
        if (lastSneaking == flag) return
        lastSneaking = flag

        val state = if (flag) ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY
        else ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY

        connection.sendPacket(ClientCommandC2SPacket(player, state))
    }
}


