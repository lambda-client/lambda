package com.lambda.client.module.modules.movement

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.PlayerTravelEvent
import com.lambda.client.mixin.extension.playerPosLookPitch
import com.lambda.client.mixin.extension.playerPosLookYaw
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.steerEntity
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.Entity
import net.minecraft.entity.item.EntityBoat
import net.minecraft.network.play.client.CPacketInput
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.client.CPacketSteerBoat
import net.minecraft.network.play.client.CPacketUseEntity
import net.minecraft.network.play.server.SPacketEntityTeleport
import net.minecraft.network.play.server.SPacketMoveVehicle
import net.minecraft.network.play.server.SPacketPlayerPosLook
import net.minecraft.network.play.server.SPacketSetPassengers
import net.minecraft.util.EnumHand
import net.minecraft.util.math.Vec3d

object BoatFly : Module(
    name = "BoatFly",
    description = "Fly using boats",
    category = Category.MOVEMENT
) {
    private val speed by setting("Speed", 1.0f, 0.1f..50.0f, 0.1f)
    private val upSpeed by setting("Up Speed", 1.0f, 0.0f..10.0f, 0.1f)
    private val glideSpeed by setting("Glide Speed", 0.1f, 0.0f..1.0f, 0.01f)
    private val antiStuck by setting("Anti Stuck", true)
    private val remount by setting("Remount", true)
    private val antiForceLook by setting("Anti Force Look", true)
    private val forceInteract by setting("Force Interact", true)
    private val teleportSpoof by setting("Teleport Spoof", false)
    private val cancelPlayer by setting("Cancel Player Packets", false)
    private val antiDesync by setting("Anti Desync", false)
    val opacity by setting("Boat Opacity", 1.0f, 0.0f..1.0f, 0.01f)
    val size by setting("Boat Scale", 1.0, 0.05..1.5, 0.01)

    init {
        onDisable {
            if (antiDesync) {
                runSafe {
                    connection.sendPacket(CPacketInput(0.0f, 0.0f, false, true))
                    player.dismountRidingEntity()
                }
            }
        }

        safeListener<PacketEvent.Send> {
            val ridingEntity = player.ridingEntity
            if (ridingEntity !is EntityBoat || !cancelPlayer) return@safeListener

            if (it.packet is CPacketPlayer
                || it.packet is CPacketInput
                || it.packet is CPacketSteerBoat) {
                if (it.packet is CPacketInput && it.packet == CPacketInput(0.0f, 0.0f, false, true)) {
                    return@safeListener
                } else {
                    it.cancel()
                }
            }
        }

        safeListener<PacketEvent.Receive> {
            val ridingEntity = player.ridingEntity
            if (ridingEntity !is EntityBoat) return@safeListener

            when (it.packet) {
                is SPacketSetPassengers -> {
                    if (remount) {
                        world.getEntityByID(it.packet.entityId)?.let { entity ->
                            if (!it.packet.passengerIds.contains(player.entityId)
                                && ridingEntity.entityId == it.packet.entityId) {
                                if (teleportSpoof) it.cancel()
                                connection.sendPacket(CPacketUseEntity(entity, EnumHand.OFF_HAND))
                            } else if ((it.packet.passengerIds.isNotEmpty())
                                && it.packet.passengerIds.contains(player.entityId)) {
                                if (antiForceLook) {
                                    entity.rotationYaw = player.prevRotationYaw
                                    entity.rotationPitch = player.prevRotationPitch
                                }
                            }
                        }
                    }
                }
                is SPacketPlayerPosLook -> {
                    if (antiForceLook) {
                        it.packet.playerPosLookYaw = player.rotationYaw
                        it.packet.playerPosLookPitch = player.rotationPitch
                    }
                }
                is SPacketEntityTeleport -> {
                    if (teleportSpoof) {
                        if (it.packet.entityId == ridingEntity.entityId) {
                            if (player.positionVector.distanceTo(Vec3d(it.packet.x, it.packet.y, it.packet.z)) > 20) {
                                world.getEntityByID(it.packet.entityId)?.let { entity ->
                                    connection.sendPacket(CPacketUseEntity(entity, EnumHand.OFF_HAND))
                                }
                            } else {
                                if (antiForceLook) it.cancel()

                                ridingEntity.posX = it.packet.x
                                ridingEntity.posY = it.packet.y
                                ridingEntity.posZ = it.packet.z
                            }
                        }
                    }
                }
                is SPacketMoveVehicle -> {
                    if (forceInteract) it.cancel()
                }
            }
        }

        safeListener<PlayerTravelEvent> {
            val ridingEntity = player.ridingEntity
            if (ridingEntity !is EntityBoat) return@safeListener

            ridingEntity.rotationYaw = player.rotationYaw
            ridingEntity.updateInputs(false, false, false, false)
            ridingEntity.setNoGravity(true)
            ridingEntity.motionY = 0.0
            if (glideSpeed > 0 && !mc.gameSettings.keyBindJump.isKeyDown) ridingEntity.motionY = -glideSpeed.toDouble()


            if (mc.gameSettings.keyBindJump.isKeyDown) ridingEntity.motionY = upSpeed.toDouble()
            if (mc.gameSettings.keyBindSneak.isKeyDown) ridingEntity.motionY = -upSpeed.toDouble()

            steerEntity(ridingEntity, speed, antiStuck)
        }
    }

    @JvmStatic
    fun isBoatFlying(entityIn: Entity): Boolean {
        return isEnabled && mc.player?.ridingEntity == entityIn
    }

    @JvmStatic
    fun shouldModifyScale(entityIn: Entity): Boolean {
        return isBoatFlying(entityIn) && mc.gameSettings.thirdPersonView == 0
    }
}
