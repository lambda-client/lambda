package com.lambda.client.mixin.extension

import com.lambda.client.mixin.client.accessor.AccessorEntity
import com.lambda.client.mixin.client.accessor.AccessorItemTool
import com.lambda.client.mixin.client.accessor.AccessorMinecraft
import com.lambda.client.mixin.client.accessor.AccessorTimer
import com.lambda.client.mixin.client.accessor.gui.AccessorGuiBossOverlay
import com.lambda.client.mixin.client.accessor.AccessorAnvilChunkLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiBossOverlay
import net.minecraft.entity.Entity
import net.minecraft.item.ItemTool
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.Timer
import net.minecraft.world.BossInfo
import net.minecraft.world.World
import net.minecraft.world.chunk.Chunk
import net.minecraft.world.chunk.storage.AnvilChunkLoader

val Entity.isInWeb: Boolean get() = (this as AccessorEntity).isInWeb

val ItemTool.attackDamage get() = (this as AccessorItemTool).attackDamage

val Minecraft.timer: Timer get() = (this as AccessorMinecraft).timer
val Minecraft.renderPartialTicksPaused: Float get() = (this as AccessorMinecraft).renderPartialTicksPaused
var Minecraft.rightClickDelayTimer: Int
    get() = (this as AccessorMinecraft).rightClickDelayTimer
    set(value) {
        (this as AccessorMinecraft).rightClickDelayTimer = value
    }

fun Minecraft.rightClickMouse() = (this as AccessorMinecraft).invokeRightClickMouse()

fun Minecraft.sendClickBlockToController(leftClick: Boolean) = (this as AccessorMinecraft).invokeSendClickBlockToController(leftClick)

var Timer.tickLength: Float
    get() = (this as AccessorTimer).tickLength
    set(value) {
        (this as AccessorTimer).tickLength = value
    }

fun AnvilChunkLoader.writeChunkToNBT(chunkIn : Chunk, worldIn : World, compound : NBTTagCompound) = (this as AccessorAnvilChunkLoader).invokeWriteChunkToNBT(chunkIn, worldIn, compound);