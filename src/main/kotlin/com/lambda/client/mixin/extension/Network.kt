package com.lambda.client.mixin.extension

import com.lambda.mixin.accessor.network.*
import net.minecraft.network.play.client.CPacketChatMessage
import net.minecraft.network.play.client.CPacketCloseWindow
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.client.CPacketUseEntity
import net.minecraft.network.play.server.*
import net.minecraft.util.text.ITextComponent

var CPacketChatMessage.chatMessage: String
    get() = this.message
    set(value) {
        (this as AccessorCPacketChatMessage).setMessage(value)
    }

val CPacketCloseWindow.windowID: Int
    get() = (this as AccessorCPacketCloseWindow).kbGetWindowID()


var CPacketPlayer.playerX: Double
    get() = this.getX(0.0)
    set(value) {
        (this as AccessorCPacketPlayer).setX(value)
    }

var CPacketPlayer.playerY: Double
    get() = this.getY(0.0)
    set(value) {
        (this as AccessorCPacketPlayer).setY(value)
    }

var CPacketPlayer.playerZ: Double
    get() = this.getZ(0.0)
    set(value) {
        (this as AccessorCPacketPlayer).setZ(value)
    }

var CPacketPlayer.playerYaw: Float
    get() = this.getYaw(0.0f)
    set(value) {
        (this as AccessorCPacketPlayer).setYaw(value)
    }

var CPacketPlayer.playerPitch: Float
    get() = this.getPitch(0.0f)
    set(value) {
        (this as AccessorCPacketPlayer).setPitch(value)
    }

var CPacketPlayer.playerIsOnGround: Boolean
    get() = this.isOnGround
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

var CPacketUseEntity.useEntityId: Int
    get() = (this as AccessorCPacketUseEntity).id
    set(value) {
        (this as AccessorCPacketUseEntity).id = value
    }

var CPacketUseEntity.useEntityAction: CPacketUseEntity.Action
    get() = this.action
    set(value) {
        (this as AccessorCPacketUseEntity).setAction(value)
    }

var SPacketChat.textComponent: ITextComponent
    get() = this.chatComponent
    set(value) {
        (this as AccessorSPacketChat).setChatComponent(value)
    }

var SPacketEntityVelocity.entityVelocityMotionX: Int
    get() = this.motionX
    set(value) {
        (this as AccessorSPacketEntityVelocity).setMotionX(value)
    }

var SPacketEntityVelocity.entityVelocityMotionY: Int
    get() = this.motionY
    set(value) {
        (this as AccessorSPacketEntityVelocity).setMotionY(value)
    }

var SPacketEntityVelocity.entityVelocityMotionZ: Int
    get() = this.motionZ
    set(value) {
        (this as AccessorSPacketEntityVelocity).setMotionZ(value)
    }

var SPacketExplosion.explosionMotionX: Float
    get() = this.motionX
    set(value) {
        (this as AccessorSPacketExplosion).setMotionX(value)
    }

var SPacketExplosion.explosionMotionY: Float
    get() = this.motionY
    set(value) {
        (this as AccessorSPacketExplosion).setMotionY(value)
    }

var SPacketExplosion.explosionMotionZ: Float
    get() = this.motionZ
    set(value) {
        (this as AccessorSPacketExplosion).setMotionZ(value)
    }

var SPacketPlayerPosLook.playerPosLookYaw: Float
    get() = this.yaw
    set(value) {
        (this as AccessorSPacketPosLook).setYaw(value)
    }

var SPacketPlayerPosLook.playerPosLookPitch: Float
    get() = this.pitch
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