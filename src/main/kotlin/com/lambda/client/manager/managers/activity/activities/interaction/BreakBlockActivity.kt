package com.lambda.client.manager.managers.activity.activities.interaction

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.AttemptActivity
import com.lambda.client.manager.managers.activity.activities.RenderBlockActivity
import com.lambda.client.manager.managers.activity.activities.TimeoutActivity
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getHitVec
import com.lambda.client.util.world.getMiningSide
import net.minecraft.init.Blocks
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.util.EnumHand
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.BlockPos
import kotlin.math.ceil

class BreakBlockActivity(
    private val blockPos: BlockPos,
    private val playSound: Boolean = true,
    private val miningSpeedFactor: Float = 1.0f,
    override val timeout: Long = 500L,
    override var creationTime: Long = 0L,
    override val maxAttempts: Int = 8,
    override var usedAttempts: Int = 0,
    override var renderBlockPos: BlockPos = blockPos,
    override var color: ColorHolder = ColorHolder(0, 0, 0)
) : TimeoutActivity, AttemptActivity, RenderBlockActivity, Activity() {
    private var ticksNeeded = 0
    private var currentState = Blocks.AIR.defaultState

    override fun SafeClientEvent.onInitialize() {
        currentState = world.getBlockState(blockPos)

        if (currentState.block == Blocks.AIR) {
            activityStatus = ActivityStatus.SUCCESS
            color = ColorHolder(16, 74, 94)
        } else {
            getMiningSide(blockPos)?.let { side ->
                ticksNeeded = ceil((1 / currentState.getPlayerRelativeBlockHardness(player, world, blockPos)) * miningSpeedFactor).toInt()

                if (ticksNeeded == 1 || player.capabilities.isCreativeMode) {
                    connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, blockPos, side))
                    player.swingArm(EnumHand.MAIN_HAND)
                    if (playSound) {
                        val soundType = currentState.block.getSoundType(currentState, world, blockPos, player)
                        world.playSound(
                            player,
                            blockPos,
                            soundType.breakSound,
                            SoundCategory.BLOCKS,
                            (soundType.getVolume() + 1.0f) / 2.0f,
                            soundType.getPitch() * 0.8f
                        )
                    }
                } else {
                    playerController.onPlayerDamageBlock(blockPos, side)
                }
                getHitVec(blockPos, side)
            } ?: run {

            }
        }
    }

    init {
        safeListener<PacketEvent.PostReceive> {
            if (it.packet is SPacketBlockChange
                && it.packet.blockPosition == blockPos
                && it.packet.blockState.block == Blocks.AIR
            ) {
                activityStatus = ActivityStatus.SUCCESS
                color = ColorHolder(0, 255, 0)
            }
        }
    }
}