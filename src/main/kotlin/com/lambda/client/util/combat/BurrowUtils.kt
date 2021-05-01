package com.lambda.client.util.combat

import net.minecraft.client.Minecraft
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketHeldItemChange
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import com.lambda.client.mixin.extension.rightClickDelayTimer
import kotlin.math.atan2
import kotlin.math.sqrt


object BurrowUtils {

    val mc: Minecraft = Minecraft.getMinecraft()

    /*
    Start block Util.
     */

    /*
    Start block Util.
     */
    fun placeBlock(pos: BlockPos, hand: EnumHand?, rotate: Boolean, packet: Boolean, isSneaking: Boolean): Boolean {
        var sneaking = false
        val side = getFirstFacing(pos) ?: return isSneaking
        val neighbour = pos.offset(side)
        val opposite = side.opposite
        val hitVec = Vec3d(neighbour).add(0.5, 0.5, 0.5).add(Vec3d(opposite.directionVec).scale(0.5))
        if (!mc.player.isSneaking) {
            mc.player.connection.sendPacket(CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_SNEAKING))
            mc.player.isSneaking = true
            sneaking = true
        }
        if (rotate) {
            faceVector(hitVec)
        }
        if (hand != null) {
            rightClickBlock(neighbour, hitVec, hand, opposite, packet)
        }
        mc.player.swingArm(EnumHand.MAIN_HAND)
        mc.rightClickDelayTimer = 4 //?
        return sneaking || isSneaking
    }

    private fun getPossibleSides(pos: BlockPos): List<EnumFacing> {
        val facings: MutableList<EnumFacing> = ArrayList()
        for (side in EnumFacing.values()) {
            val neighbour = pos.offset(side)
            if (mc.world.getBlockState(neighbour).block.canCollideCheck(mc.world.getBlockState(neighbour), false)) {
                val blockState = mc.world.getBlockState(neighbour)
                if (!blockState.material.isReplaceable) {
                    facings.add(side)
                }
            }
        }
        return facings
    }

    private fun getFirstFacing(pos: BlockPos): EnumFacing? {
        for (facing in getPossibleSides(pos)) {
            return facing
        }
        return null
    }

    private fun getEyesPos(): Vec3d {
        return Vec3d(mc.player.posX, mc.player.posY + mc.player.getEyeHeight(), mc.player.posZ)
    }

    private fun getLegitRotations(vec: Vec3d): FloatArray {
        val eyesPos = getEyesPos()
        val diffX = vec.x - eyesPos.x
        val diffY = vec.y - eyesPos.y
        val diffZ = vec.z - eyesPos.z
        val diffXZ = sqrt(diffX * diffX + diffZ * diffZ)
        val yaw = Math.toDegrees(atan2(diffZ, diffX)).toFloat() - 90f
        val pitch = (-Math.toDegrees(atan2(diffY, diffXZ))).toFloat()
        return floatArrayOf(
            mc.player.rotationYaw + MathHelper.wrapDegrees(yaw - mc.player.rotationYaw),
            mc.player.rotationPitch + MathHelper.wrapDegrees(pitch - mc.player.rotationPitch)
        )
    }

    private fun faceVector(vec: Vec3d) {
        val rotations = getLegitRotations(vec)
        mc.player.connection.sendPacket(CPacketPlayer.Rotation(rotations[0], MathHelper.normalizeAngle(rotations[1].toInt(), 360).toFloat(), mc.player.onGround))
    }

    private fun rightClickBlock(pos: BlockPos, vec: Vec3d, hand: EnumHand, direction: EnumFacing, packet: Boolean) {
        if (packet) {
            val f = (vec.x - pos.x.toDouble()).toFloat()
            val f1 = (vec.y - pos.y.toDouble()).toFloat()
            val f2 = (vec.z - pos.z.toDouble()).toFloat()
            mc.player.connection.sendPacket(CPacketPlayerTryUseItemOnBlock(pos, direction, hand, f, f1, f2))
        } else {
            mc.playerController.processRightClickBlock(mc.player, mc.world, pos, direction, vec, hand)
        }
        mc.player.swingArm(EnumHand.MAIN_HAND)
        mc.rightClickDelayTimer = 4 //?
    }

    fun findHotbarBlock(clazz: Class<*>): Int {
        for (i in 0..8) {
            val stack = mc.player.inventory.getStackInSlot(i)
            if (stack == ItemStack.EMPTY) {
                continue
            }
            if (clazz.isInstance(stack.item)) {
                return i
            }
            if (stack.item is ItemBlock) {
                val block = (stack.item as ItemBlock).block
                if (clazz.isInstance(block)) {
                    return i
                }
            }
        }
        return -1
    }

    fun switchToSlot(slot: Int) {
        mc.player.connection.sendPacket(CPacketHeldItemChange(slot))
        mc.player.inventory.currentItem = slot
        mc.playerController.updateController()
    }

}