package com.lambda.client.mixin.extension

import net.minecraft.client.Minecraft
import net.minecraft.entity.Entity
import net.minecraft.item.ItemTool
import net.minecraft.util.Timer

val Entity.isInWeb: Boolean get() = (this as com.lambda.client.mixin.client.accessor.AccessorEntity).isInWeb

val ItemTool.attackDamage get() = (this as com.lambda.client.mixin.client.accessor.AccessorItemTool).attackDamage

val Minecraft.timer: Timer get() = (this as com.lambda.client.mixin.client.accessor.AccessorMinecraft).timer
val Minecraft.renderPartialTicksPaused: Float get() = (this as com.lambda.client.mixin.client.accessor.AccessorMinecraft).renderPartialTicksPaused
var Minecraft.rightClickDelayTimer: Int
    get() = (this as com.lambda.client.mixin.client.accessor.AccessorMinecraft).rightClickDelayTimer
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.AccessorMinecraft).rightClickDelayTimer = value
    }

fun Minecraft.rightClickMouse() = (this as com.lambda.client.mixin.client.accessor.AccessorMinecraft).invokeRightClickMouse()

fun Minecraft.sendClickBlockToController(leftClick: Boolean) = (this as com.lambda.client.mixin.client.accessor.AccessorMinecraft).invokeSendClickBlockToController(leftClick)

var Timer.tickLength: Float
    get() = (this as com.lambda.client.mixin.client.accessor.AccessorTimer).tickLength
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.AccessorTimer).tickLength = value
    }