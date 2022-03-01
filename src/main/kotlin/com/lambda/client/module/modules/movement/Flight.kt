package com.lambda.client.module.modules.movement

import com.lambda.client.event.Phase
import com.lambda.client.event.events.OnUpdateWalkingPlayerEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.PlayerTravelEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.managers.PacketManager
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.mixin.extension.playerPosLookPitch
import com.lambda.client.mixin.extension.playerPosLookYaw
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.MovementUtils
import com.lambda.client.util.MovementUtils.calcMoveYaw
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import net.minecraft.network.play.client.CPacketConfirmTeleport
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.server.SPacketCloseWindow
import net.minecraft.network.play.server.SPacketPlayerPosLook
import kotlin.math.cos
import kotlin.math.sin

object Flight : Module(
    name = "Flight",
    category = Category.MOVEMENT,
    description = "Makes the player fly",
    modulePriority = 500
) {
    private val mode by setting("Mode", FlightMode.PACKET)
    private val speed by setting("Speed", 1.0f, 0.0f..10.0f, 0.1f)
    private val glideSpeed by setting("Glide Speed", 0.05, 0.0..0.3, 0.001)
    private val packetMode by setting("Packet Mode", PacketMode.NEGATIVE)
    private val upSpeed by setting("Up Speed", 0.0622, 0.0..0.3, 0.001, { mode == FlightMode.PACKET })
    private val antiKick by setting("Anti Kick", true, { mode == FlightMode.PACKET })
    private val antiKickSpeed by setting("Anti Kick Speed", 0.0622, 0.0..0.3, 0.001)
    private val antiKickDelay by setting("Anti Kick Delay", 14, 0..100, 1)
    private val hShrinkAmount by setting("Horizontal Shrink Amount", 4.0, 1.0..10.0, 0.1)
    private val vShrinkAmount by setting("Vertical Shrink Amount", 2.70, 1.0..10.0, 0.1)

    private enum class FlightMode {
        PACKET, VANILLA, STATIC
    }

    private enum class PacketMode {
        POSITIVE, NEGATIVE
    }

    init {
        onDisable {
            runSafe {
                player.capabilities?.apply {
                    isFlying = false
                    flySpeed = 0.05f
                }
            }
        }

        safeListener<PlayerTravelEvent> {
            when (mode) {
                FlightMode.PACKET -> {
                    it.cancel()

                    player.motionY = if (mc.gameSettings.keyBindJump.isKeyDown xor mc.gameSettings.keyBindSneak.isKeyDown) {
                        if (mc.gameSettings.keyBindJump.isKeyDown) {
                            if (player.ticksExisted % antiKickDelay != 0) {
                                upSpeed / vShrinkAmount
                            } else {
                                -antiKickSpeed / vShrinkAmount
                            }
                        } else (-upSpeed / vShrinkAmount)
                    } else {
                        if (MovementUtils.isInputting) {
                            val yaw = calcMoveYaw()
                            player.motionX = (-sin(yaw) * 0.2f * speed) / hShrinkAmount
                            player.motionZ = (cos(yaw) * 0.2f * speed) / hShrinkAmount
                        }
                        -glideSpeed / vShrinkAmount
                    }

                    val antiKickOffset = if (player.ticksExisted % (antiKickDelay + 1) == 0 && antiKick) -antiKickSpeed else 0.0

                    val posX = player.posX + (player.motionX * hShrinkAmount)
                    val posY = player.posY + (player.motionY * vShrinkAmount) + antiKickOffset
                    val posZ = player.posZ + (player.motionZ * hShrinkAmount)

                    val invalidPacketOffset = when (packetMode) {
                        PacketMode.POSITIVE -> 1000
                        PacketMode.NEGATIVE -> -1000
                    }

                    connection.sendPacket(CPacketPlayer.Position(posX, posY, posZ, false))
                    connection.sendPacket(CPacketPlayer.Position(posX, player.posY + invalidPacketOffset, posZ, false))
                    if (PacketManager.lastTeleportId != -1) {
                        connection.sendPacket(CPacketConfirmTeleport(PacketManager.lastTeleportId++))
                    }
                }
                FlightMode.STATIC -> {
                    player.capabilities.isFlying = true
                    player.capabilities.flySpeed = speed

                    player.motionX = 0.0
                    player.motionY = -glideSpeed
                    player.motionZ = 0.0

                    if (mc.gameSettings.keyBindJump.isKeyDown) player.motionY += speed / 2.0f
                    if (mc.gameSettings.keyBindSneak.isKeyDown) player.motionY -= speed / 2.0f
                }
                FlightMode.VANILLA -> {
                    player.capabilities.isFlying = true
                    player.capabilities.flySpeed = speed / 11.11f

                    if (glideSpeed != 0.0
                        && !mc.gameSettings.keyBindJump.isKeyDown
                        && !mc.gameSettings.keyBindSneak.isKeyDown) player.motionY = -glideSpeed
                }
            }
        }

        listener<OnUpdateWalkingPlayerEvent> {
            if (it.phase != Phase.PRE || mode != FlightMode.PACKET) return@listener
            sendPlayerPacket {
                cancelAll()
            }
        }

        safeListener<PacketEvent.Receive> {
            if (mode != FlightMode.PACKET) return@safeListener
            when (it.packet) {
                is SPacketPlayerPosLook -> {
                    it.packet.playerPosLookYaw = player.rotationYaw
                    it.packet.playerPosLookPitch = player.rotationPitch
                }
                is SPacketCloseWindow -> {
                    it.cancel()
                }
            }
        }
    }
}
