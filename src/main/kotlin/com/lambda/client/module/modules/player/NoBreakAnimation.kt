package com.lambda.client.module.modules.player

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityEnderCrystal
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent

object NoBreakAnimation : Module(
    name = "NoBreakAnimation",
    description = "Prevents block break animation server side",
    category = Category.PLAYER
) {
    private var isMining = false
    private var lastPos: BlockPos? = null
    private var lastFacing: EnumFacing? = null

    init {
        // Lower priority so we process the packet at the last
        safeListener<PacketEvent.Send>(500) {
            if (it.packet !is CPacketPlayerDigging) return@safeListener
            // skip crystals and living entities
            for (entity in world.getEntitiesWithinAABBExcludingEntity(null, AxisAlignedBB(it.packet.position))) {
                if (entity is EntityEnderCrystal || entity is EntityLivingBase) {
                    resetMining()
                    return@safeListener
                }
            }
            if (it.packet.action == CPacketPlayerDigging.Action.START_DESTROY_BLOCK) {
                isMining = true
                lastPos = it.packet.position
                lastFacing = it.packet.facing
            }
            if (it.packet.action == CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK) {
                resetMining()
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (!mc.gameSettings.keyBindAttack.isKeyDown) {
                resetMining()
                return@safeListener
            }
            if (isMining) {
                lastPos?.let { lastPos ->
                    lastFacing?.let { lastFacing ->
                        connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, lastPos, lastFacing))
                    }
                }
            }
        }
    }

    private fun resetMining() {
        isMining = false
        lastPos = null
        lastFacing = null
    }
}