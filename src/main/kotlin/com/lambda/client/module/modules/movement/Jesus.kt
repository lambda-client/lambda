package com.lambda.client.module.modules.movement

import com.lambda.client.commons.extension.ceilToInt
import com.lambda.client.commons.extension.floorToInt
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.PlayerTravelEvent
import com.lambda.client.mixin.extension.playerMoving
import com.lambda.client.mixin.extension.playerY
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.EntityUtils
import com.lambda.client.util.threads.safeListener
import net.minecraft.block.Block
import net.minecraft.block.BlockLiquid
import net.minecraft.entity.Entity
import net.minecraft.entity.item.EntityBoat
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos

object Jesus : Module(
    name = "Jesus",
    description = "Allows you to walk on water",
    category = Category.MOVEMENT
) {
    private val mode by setting("Mode", Mode.SOLID)

    private enum class Mode {
        SOLID, DOLPHIN
    }

    private val waterWalkBox = AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 0.99, 1.0)

    init {
        onToggle {
            BaritoneUtils.settings?.assumeWalkOnWater?.value = it
        }

        safeListener<PlayerTravelEvent> {
            if (mc.gameSettings.keyBindSneak.isKeyDown || player.fallDistance > 3.0f || !isInWater(player)) return@safeListener

            if (mode == Mode.DOLPHIN) {
                player.motionY += 0.03999999910593033 // regular jump speed
            } else {
                player.motionY = 0.1

                player.ridingEntity?.let {
                    if (it !is EntityBoat) it.motionY = 0.3
                }
            }
        }

        safeListener<PacketEvent.Send> {
            if (it.packet !is CPacketPlayer || !it.packet.playerMoving) return@safeListener
            if (mc.gameSettings.keyBindSneak.isKeyDown || player.ticksExisted % 2 != 0) return@safeListener

            val entity = player.ridingEntity ?: player

            if (EntityUtils.isAboveLiquid(entity, true) && !isInWater(entity)) {
                it.packet.playerY += 0.02
            }
        }
    }

    @JvmStatic
    fun handleAddCollisionBoxToList(pos: BlockPos, block: Block, entity: Entity?, collidingBoxes: MutableList<AxisAlignedBB>) {
        if (isDisabled || mode == Mode.DOLPHIN) return
        if (mc.gameSettings.keyBindSneak.isKeyDown) return
        if (block !is BlockLiquid) return
        if (entity == null || entity is EntityBoat) return

        val player = mc.player ?: return
        if (player.fallDistance > 3.0f) return

        if (entity != player && entity != player.ridingEntity) return
        if (isInWater(entity) || entity.posY < pos.y) return
        if (!EntityUtils.isAboveLiquid(entity)) return

        collidingBoxes.add(waterWalkBox.offset(pos))
    }

    private fun isInWater(entity: Entity): Boolean {
        mc.world?.let {
            val y = (entity.posY + 0.01).floorToInt()

            for (x in entity.posX.floorToInt() until entity.posX.ceilToInt()) {
                for (z in entity.posZ.floorToInt() until entity.posZ.ceilToInt()) {
                    val pos = BlockPos(x, y, z)
                    if (it.getBlockState(pos).block is BlockLiquid) return true
                }
            }
        }

        return false
    }

}