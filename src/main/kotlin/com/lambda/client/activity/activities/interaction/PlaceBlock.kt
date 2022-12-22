package com.lambda.client.activity.activities.interaction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.AttemptActivity
import com.lambda.client.activity.activities.RenderBlockActivity
import com.lambda.client.activity.activities.TimeoutActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.util.color.ColorHolder
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
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.BlockPos

class PlaceBlock(
    private val blockPos: BlockPos,
    private val block: Block,
    private val playSound: Boolean = true,
    override val timeout: Long = 500L,
    override var creationTime: Long = 0L,
    override val maxAttempts: Int = 8,
    override var usedAttempts: Int = 0,
    override var renderBlockPos: BlockPos = blockPos,
    override var color: ColorHolder = ColorHolder(0, 0, 0)
) : TimeoutActivity, AttemptActivity, RenderBlockActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        getNeighbour(blockPos, attempts = 1, visibleSideCheck = true)?.let {
            val placedAtState = world.getBlockState(it.pos)

            if (placedAtState.block in blockBlacklist) {
                connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_SNEAKING))
            }

            val rotation = getRotationTo(it.hitVec)

            connection.sendPacket(CPacketPlayer.Rotation(rotation.x, rotation.y, player.onGround))
            connection.sendPacket(it.toPlacePacket(EnumHand.MAIN_HAND))
            player.swingArm(EnumHand.MAIN_HAND)

            if (placedAtState.block in blockBlacklist) {
                connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.STOP_SNEAKING))
            }

            if (playSound) {
                val thisState = world.getBlockState(blockPos)

                val soundType = thisState.block.getSoundType(
                    thisState,
                    world,
                    blockPos,
                    player
                )
                world.playSound(
                    player,
                    blockPos,
                    soundType.placeSound,
                    SoundCategory.BLOCKS,
                    (soundType.getVolume() + 1.0f) / 2.0f,
                    soundType.getPitch() * 0.8f
                )
            }
        } ?: run {
            activityStatus = ActivityStatus.FAILURE
            color = ColorHolder(16, 74, 94)
        }
    }

    init {
        safeListener<PacketEvent.PostReceive> {
            if (it.packet is SPacketBlockChange
                && it.packet.blockPosition == blockPos
                && it.packet.blockState.block == block
            ) {
                activityStatus = ActivityStatus.SUCCESS
                color = ColorHolder(35, 188, 254)
            }
        }
    }
}