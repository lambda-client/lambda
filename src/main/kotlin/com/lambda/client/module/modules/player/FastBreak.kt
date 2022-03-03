package com.lambda.client.module.modules.player

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.mixin.extension.blockHitDelay
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.threads.safeListener
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

object FastBreak : Module(
    name = "FastBreak",
    description = "Breaks block faster and nullifies the break delay",
    category = Category.PLAYER
) {
    private val breakDelay by setting("Break Delay", 0, 0..5, 1)
    private val packetMine by setting("Packet Mine", true)
    private val sneakTrigger by setting("Sneak Trigger", true, { packetMine })
    private val morePackets by setting("More Packets", false, { packetMine })
    private val spamDelay by setting("Spam Delay", 4, 1..10, 1, { packetMine })

    private val spamTimer = TickTimer(TimeUnit.TICKS)
    private var miningInfo: Triple<Long, BlockPos, EnumFacing>? = null

    init {
        onDisable {
            miningInfo = null
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.END) return@safeListener

            if (breakDelay != 5 && playerController.blockHitDelay == 5) {
                playerController.blockHitDelay = breakDelay
            }

            if (packetMine) {
                doPacketMine(miningInfo)
            } else {
                miningInfo = null
            }
        }

        safeListener<PlayerInteractEvent.LeftClickBlock> { event ->
            if (!packetMine || sneakTrigger && !player.isSneaking) return@safeListener

            event.face?.let {
                miningInfo = Triple(System.currentTimeMillis(), event.pos, it)
            }
        }

        safeListener<PacketEvent.Send> {
            if (it.packet is CPacketPlayerDigging
                && it.packet.action == CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK
                && it.packet.position == miningInfo?.second) {
                it.cancel()
            }
        }
    }

    private fun SafeClientEvent.doPacketMine(triple: Triple<Long, BlockPos, EnumFacing>?) {
        if (triple == null) return

        if (spamTimer.tick(spamDelay.toLong())) {
            val (startTime, pos, facing) = triple

            if (System.currentTimeMillis() - startTime > 10000L || world.isAirBlock(pos)) {
                miningInfo = null
            } else {
                if (morePackets) connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, pos, facing))
                connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, facing))
            }
        }
    }
}