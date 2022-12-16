package com.lambda.client.manager.managers.activity.activities.interaction

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.AttemptActivity
import com.lambda.client.manager.managers.activity.activities.TimeoutActivity
import com.lambda.client.util.items.blockBlacklist
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getNeighbour
import com.lambda.client.util.world.toPlacePacket
import net.minecraft.block.Block
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos

class PlaceBlockActivity(
    private val blockPos: BlockPos,
    private val block: Block,
    override val timeout: Long = 10000L,
    override var creationTime: Long = 0L,
    override val maxAttempts: Int = 5,
    override var usedAttempts: Int = 0
) : TimeoutActivity, AttemptActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        getNeighbour(blockPos)?.let {
            val currentBlock = world.getBlockState(it.pos).block

            if (currentBlock in blockBlacklist) {
                connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_SNEAKING))
            }

            val rotation = getRotationTo(it.hitVec)

            connection.sendPacket(CPacketPlayer.Rotation(rotation.x, rotation.y, player.onGround))
            connection.sendPacket(it.toPlacePacket(EnumHand.MAIN_HAND))
            player.swingArm(EnumHand.MAIN_HAND)

            if (currentBlock in blockBlacklist) {
                connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.STOP_SNEAKING))
            }
        } ?: run {
            activityStatus = ActivityStatus.FAILURE
        }
    }

    init {
        safeListener<PacketEvent.PostReceive> {
            if (it.packet is SPacketBlockChange
                && it.packet.blockPosition == blockPos
                && it.packet.blockState.block == block
            ) {
                activityStatus = ActivityStatus.SUCCESS
            }
        }
    }
}