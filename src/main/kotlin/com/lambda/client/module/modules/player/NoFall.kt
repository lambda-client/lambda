package com.lambda.client.module.modules.player

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.mixin.extension.playerIsOnGround
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getGroundPos
import net.minecraft.init.Items
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.CPacketAnimation
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent

object NoFall : Module(
    name = "NoFall",
    description = "Prevents fall damage",
    category = Category.PLAYER
) {
    private val distance by setting("Distance", 3, 1..10, 1)
    var mode by setting("Mode", Mode.CATCH)
    private val fallModeSetting by setting("Fall", FallMode.PACKET, { mode == Mode.FALL })
    private val catchModeSetting by setting("Catch", CatchMode.MOTION, { mode == Mode.CATCH })
    private val voidOnly by setting("Void Only", false, { mode == Mode.CATCH })

    enum class Mode {
        FALL, CATCH
    }

    private enum class FallMode {
        BUCKET, PACKET
    }

    private enum class CatchMode {
        BLOCK, MOTION
    }

    private var last: Long = 0

    init {
        safeListener<PacketEvent.Send> {
            if (it.packet !is CPacketPlayer || player.isElytraFlying) return@safeListener
            if ((mode == Mode.FALL && fallModeSetting == FallMode.PACKET || mode == Mode.CATCH)) {
                it.packet.playerIsOnGround = true
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (player.isCreative || player.isSpectator || !fallDistCheck()) return@safeListener
            if (mode == Mode.FALL) {
                fallMode()
            } else if (mode == Mode.CATCH) {
                catchMode()
            }
        }
    }

    private fun SafeClientEvent.fallDistCheck() = (!voidOnly && player.fallDistance >= distance) || world.getGroundPos(player).y == -69420.0

    // TODO: This really needs a rewrite to spoof placing and the such instead of manual rotations
    private fun SafeClientEvent.fallMode() {
        if (fallModeSetting == FallMode.BUCKET && player.dimension != -1 && !EntityUtils.isAboveLiquid(player) && System.currentTimeMillis() - last > 100) {
            val posVec = player.positionVector
            val result = world.rayTraceBlocks(posVec, posVec.add(0.0, -5.33, 0.0), true, true, false)

            if (result != null && result.typeOfHit == RayTraceResult.Type.BLOCK) {
                var hand = EnumHand.MAIN_HAND
                if (player.heldItemOffhand.item == Items.WATER_BUCKET) hand = EnumHand.OFF_HAND else if (player.heldItemMainhand.item != Items.WATER_BUCKET) {
                    for (i in 0..8) if (player.inventory.getStackInSlot(i).item == Items.WATER_BUCKET) {
                        player.inventory.currentItem = i
                        player.rotationPitch = 90f
                        last = System.currentTimeMillis()
                        return
                    }
                    return
                }

                player.rotationPitch = 90f
                playerController.processRightClick(player, world, hand)
            }
        }
    }

    private fun SafeClientEvent.catchMode() {
        when (catchModeSetting) {
            CatchMode.BLOCK -> {
                var slot = -1
                for (i in 0..8) {
                    val stack = player.inventory.getStackInSlot(i)
                    if (stack != ItemStack.EMPTY && stack.item is ItemBlock) {
                        slot = i
                    }
                }

                if (slot == -1) {
                    MessageSendHelper.sendChatMessage("$chatName Missing blocks for Catch Mode Block!")
                    return
                } else {
                    player.inventory.currentItem = slot
                }

                val posVec = player.positionVector
                val result = world.rayTraceBlocks(posVec, posVec.add(0.0, -5.33, 0.0), true, true, false)
                if (result != null && result.typeOfHit == RayTraceResult.Type.BLOCK) {
                    placeBlock(); placeBlock(); placeBlock() // yes
                }
            }
            CatchMode.MOTION -> {
                player.motionY = 10.0
                player.motionY = -1.0
            }
        }
    }

    private fun SafeClientEvent.placeBlock() {
        val hitVec = Vec3d(BlockPos(player)).add(0.0, -1.0, 0.0)
        playerController.processRightClickBlock(player, world, BlockPos(hitVec), EnumFacing.DOWN, hitVec, EnumHand.MAIN_HAND)
        player.connection.sendPacket(CPacketAnimation(EnumHand.MAIN_HAND))
    }
}