package com.lambda.client.mixin.extension

import com.lambda.mixin.accessor.network.*
import net.minecraft.network.play.client.*
import net.minecraft.network.play.server.*
import net.minecraft.util.text.ITextComponent
import net.minecraft.world.storage.MapDecoration
import java.util.*

var CPacketChatMessage.chatMessage: String
    get() = message
    set(value) {
        (this as AccessorCPacketChatMessage).setMessage(value)
    }
val CPacketClientSettings.view: Int
    get() = (this as AccessorCPacketClientSettings).view

val CPacketCloseWindow.windowID: Int
    get() = (this as AccessorCPacketCloseWindow).kbGetWindowID()
val CPacketConfirmTransaction.accepted: Boolean
    get() = (this as AccessorCPacketConfirmTransaction).accepted

var CPacketPlayer.playerX: Double
    get() = getX(0.0)
    set(value) {
        (this as AccessorCPacketPlayer).setX(value)
    }

var CPacketPlayer.playerY: Double
    get() = getY(0.0)
    set(value) {
        (this as AccessorCPacketPlayer).setY(value)
    }

var CPacketPlayer.playerZ: Double
    get() = getZ(0.0)
    set(value) {
        (this as AccessorCPacketPlayer).setZ(value)
    }

var CPacketPlayer.playerYaw: Float
    get() = getYaw(0.0f)
    set(value) {
        (this as AccessorCPacketPlayer).setYaw(value)
    }

var CPacketPlayer.playerPitch: Float
    get() = getPitch(0.0f)
    set(value) {
        (this as AccessorCPacketPlayer).setPitch(value)
    }

var CPacketPlayer.playerIsOnGround: Boolean
    get() = isOnGround
    set(value) {
        (this as AccessorCPacketPlayer).setOnGround(value)
    }

var CPacketPlayer.playerMoving: Boolean
    get() = (this as AccessorCPacketPlayer).moving
    set(value) {
        (this as AccessorCPacketPlayer).moving = value
    }

var CPacketPlayer.playerRotating: Boolean
    get() = (this as AccessorCPacketPlayer).rotating
    set(value) {
        (this as AccessorCPacketPlayer).rotating = value
    }

val CPacketPlayerAbilities.flySpeed: Float
    get() = (this as AccessorCPacketPlayerAbilities).flySpeed
val CPacketPlayerAbilities.walkSpeed: Float
    get() = (this as AccessorCPacketPlayerAbilities).walkSpeed
val CPacketResourcePackStatus.action: CPacketResourcePackStatus.Action
    get() = (this as AccessorCPacketResourcePackStatus).action
val CPacketSpectate.uuid: UUID
    get() = (this as AccessorCPacketSpectate).id

var CPacketUseEntity.useEntityId: Int
    get() = (this as AccessorCPacketUseEntity).id
    set(value) {
        (this as AccessorCPacketUseEntity).id = value
    }

var CPacketUseEntity.useEntityAction: CPacketUseEntity.Action
    get() = action
    set(value) {
        (this as AccessorCPacketUseEntity).setAction(value)
    }

 fun CPacketVehicleMove.setValues(x: Double, y: Double, z: Double, yaw: Float, pitch: Float): CPacketVehicleMove {
    (this as AccessorCPacketVehicleMove).setX(x)
    (this as AccessorCPacketVehicleMove).setY(y)
    (this as AccessorCPacketVehicleMove).setZ(z)
    (this as AccessorCPacketVehicleMove).setYaw(yaw)
    (this as AccessorCPacketVehicleMove).setPitch(pitch)
    return this
}

var SPacketChat.textComponent: ITextComponent
    get() = chatComponent
    set(value) {
        (this as AccessorSPacketChat).setChatComponent(value)
    }

val SPacketCloseWindow.windowId: Int
    get() = (this as AccessorSPacketCloseWindow).windowId

var SPacketEntityVelocity.entityVelocityMotionX: Int
    get() = motionX
    set(value) {
        (this as AccessorSPacketEntityVelocity).setMotionX(value)
    }

var SPacketEntityVelocity.entityVelocityMotionY: Int
    get() = motionY
    set(value) {
        (this as AccessorSPacketEntityVelocity).setMotionY(value)
    }

var SPacketEntityVelocity.entityVelocityMotionZ: Int
    get() = motionZ
    set(value) {
        (this as AccessorSPacketEntityVelocity).setMotionZ(value)
    }

var SPacketExplosion.explosionMotionX: Float
    get() = motionX
    set(value) {
        (this as AccessorSPacketExplosion).setMotionX(value)
    }

var SPacketExplosion.explosionMotionY: Float
    get() = motionY
    set(value) {
        (this as AccessorSPacketExplosion).setMotionY(value)
    }

var SPacketExplosion.explosionMotionZ: Float
    get() = motionZ
    set(value) {
        (this as AccessorSPacketExplosion).setMotionZ(value)
    }

val SPacketMaps.mapScale: Byte
    get() = (this as AccessorSPacketMaps).mapScale
val SPacketMaps.trackingPosition: Boolean
    get() = (this as AccessorSPacketMaps).trackingPosition
val SPacketMaps.icons: Array<MapDecoration>
    get() = (this as AccessorSPacketMaps).icons
val SPacketMaps.minX: Int
    get() = (this as AccessorSPacketMaps).minX
val SPacketMaps.minZ: Int
    get() = (this as AccessorSPacketMaps).minZ
val SPacketMaps.columns: Int
    get() = (this as AccessorSPacketMaps).columns
val SPacketMaps.rows: Int
    get() = (this as AccessorSPacketMaps).rows
val SPacketMaps.mapDataBytes: ByteArray
    get() = (this as AccessorSPacketMaps).mapDataBytes

var SPacketPlayerPosLook.playerPosLookYaw: Float
    get() = yaw
    set(value) {
        (this as AccessorSPacketPosLook).setYaw(value)
    }

var SPacketPlayerPosLook.playerPosLookPitch: Float
    get() = pitch
    set(value) {
        (this as AccessorSPacketPosLook).setPitch(value)
    }

var SPacketEntity.entityId: Int
    get() = (this as AccessorSPacketEntity).entityId
    set(value) {
        (this as AccessorSPacketEntity).entityId = value
    }

var SPacketEntityHeadLook.entityHeadLookEntityId: Int
    get() = (this as AccessorSPacketEntityHeadLook).entityId
    set(value) {
        (this as AccessorSPacketEntityHeadLook).entityId = value
    }

val SPacketWorldBorder.action: SPacketWorldBorder.Action
    get() = (this as AccessorSPacketWorldBorder).action
val SPacketWorldBorder.size: Int
    get() = (this as AccessorSPacketWorldBorder).size
val SPacketWorldBorder.centerX: Double
    get() = (this as AccessorSPacketWorldBorder).centerX
val SPacketWorldBorder.centerZ: Double
    get() = (this as AccessorSPacketWorldBorder).centerZ
val SPacketWorldBorder.targetSize: Double
    get() = (this as AccessorSPacketWorldBorder).targetSize
val SPacketWorldBorder.diameter: Double
    get() = (this as AccessorSPacketWorldBorder).diameter
val SPacketWorldBorder.timeUntilTarget: Long
    get() = (this as AccessorSPacketWorldBorder).timeUntilTarget
val SPacketWorldBorder.warningTime: Int
    get() = (this as AccessorSPacketWorldBorder).warningTime
val SPacketWorldBorder.warningDistance: Int
    get() = (this as AccessorSPacketWorldBorder).warningDistance