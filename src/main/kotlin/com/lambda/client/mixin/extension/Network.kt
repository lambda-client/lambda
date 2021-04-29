package com.lambda.client.mixin.extension

import net.minecraft.network.play.client.CPacketChatMessage
import net.minecraft.network.play.client.CPacketCloseWindow
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.client.CPacketUseEntity
import net.minecraft.network.play.server.SPacketChat
import net.minecraft.network.play.server.SPacketEntityVelocity
import net.minecraft.network.play.server.SPacketExplosion
import net.minecraft.network.play.server.SPacketPlayerPosLook
import net.minecraft.util.text.ITextComponent

var CPacketChatMessage.packetMessage: String
    get() = this.message
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.network.AccessorCPacketChatMessage).setMessage(value)
    }

val CPacketCloseWindow.windowID: Int
    get() = (this as com.lambda.client.mixin.client.accessor.network.AccessorCPacketCloseWindow).kbGetWindowID()


var CPacketPlayer.x: Double
    get() = this.getX(0.0)
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.network.AccessorCPacketPlayer).setX(value)
    }
var CPacketPlayer.y: Double
    get() = this.getY(0.0)
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.network.AccessorCPacketPlayer).setY(value)
    }
var CPacketPlayer.z: Double
    get() = this.getZ(0.0)
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.network.AccessorCPacketPlayer).setZ(value)
    }
var CPacketPlayer.yaw: Float
    get() = this.getYaw(0.0f)
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.network.AccessorCPacketPlayer).setYaw(value)
    }
var CPacketPlayer.pitch: Float
    get() = this.getPitch(0.0f)
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.network.AccessorCPacketPlayer).setPitch(value)
    }
var CPacketPlayer.onGround: Boolean
    get() = this.isOnGround
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.network.AccessorCPacketPlayer).setOnGround(value)
    }
val CPacketPlayer.moving: Boolean get() = (this as com.lambda.client.mixin.client.accessor.network.AccessorCPacketPlayer).moving
val CPacketPlayer.rotating: Boolean get() = (this as com.lambda.client.mixin.client.accessor.network.AccessorCPacketPlayer).rotating

var CPacketUseEntity.id: Int
    get() = (this as com.lambda.client.mixin.client.accessor.network.AccessorCPacketUseEntity).id
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.network.AccessorCPacketUseEntity).id = value
    }

var CPacketUseEntity.packetAction: CPacketUseEntity.Action
    get() = this.action
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.network.AccessorCPacketUseEntity).setAction(value)
    }

var SPacketChat.textComponent: ITextComponent
    get() = this.chatComponent
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.network.AccessorSPacketChat).setChatComponent(value)
    }

var SPacketEntityVelocity.packetMotionX: Int
    get() = this.motionX
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.network.AccessorSPacketEntityVelocity).setMotionX(value)
    }
var SPacketEntityVelocity.packetMotionY: Int
    get() = this.motionY
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.network.AccessorSPacketEntityVelocity).setMotionY(value)
    }
var SPacketEntityVelocity.packetMotionZ: Int
    get() = this.motionZ
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.network.AccessorSPacketEntityVelocity).setMotionZ(value)
    }

var SPacketExplosion.packetMotionX: Float
    get() = this.motionX
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.network.AccessorSPacketExplosion).setMotionX(value)
    }
var SPacketExplosion.packetMotionY: Float
    get() = this.motionY
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.network.AccessorSPacketExplosion).setMotionY(value)
    }
var SPacketExplosion.packetMotionZ: Float
    get() = this.motionZ
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.network.AccessorSPacketExplosion).setMotionZ(value)
    }

var SPacketPlayerPosLook.rotationYaw: Float
    get() = this.yaw
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.network.AccessorSPacketPosLook).setYaw(value)
    }
var SPacketPlayerPosLook.rotationPitch: Float
    get() = this.pitch
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.network.AccessorSPacketPosLook).setPitch(value)
    }