package com.lambda.client.manager.managers.activity.activities.interaction

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.AttemptActivity
import com.lambda.client.manager.managers.activity.activities.RenderBlockActivity
import com.lambda.client.manager.managers.activity.activities.SetState
import com.lambda.client.manager.managers.activity.activities.TimeoutActivity
import com.lambda.client.manager.managers.activity.activities.travel.PickUpDrops
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.items.item
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getHitVec
import com.lambda.client.util.world.getMiningSide
import net.minecraft.init.Blocks
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.util.EnumHand
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.math.ceil

class BreakBlock(
    private val blockPos: BlockPos,
    private val playSound: Boolean = true,
    private val miningSpeedFactor: Float = 1.0f,
    private val pickUpDrop: Boolean = false,
    private val mode: Mode = Mode.PACKET,
    override var timeout: Long = 200L,
    override var creationTime: Long = 0L,
    override val maxAttempts: Int = 8,
    override var usedAttempts: Int = 0,
    override var renderBlockPos: BlockPos = blockPos,
    override var color: ColorHolder = ColorHolder(0, 0, 0)
) : TimeoutActivity, AttemptActivity, RenderBlockActivity, Activity() {
    private var ticksNeeded = 0
    private var initState = Blocks.AIR.defaultState

    enum class Mode {
        PLAYER_CONTROLLER, PACKET
    }

    override fun SafeClientEvent.onInitialize() {
        initState = world.getBlockState(blockPos)

        if (initState.block == Blocks.AIR) {
            activityStatus = ActivityStatus.SUCCESS
            color = ColorHolder(16, 74, 94)
        } else {
            ticksNeeded = ceil((1 / initState.getPlayerRelativeBlockHardness(player, world, blockPos)) * miningSpeedFactor).toInt()
        }
    }

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            getMiningSide(blockPos)?.let { side ->
                val rotation = getRotationTo(getHitVec(blockPos, side))

                connection.sendPacket(CPacketPlayer.Rotation(rotation.x, rotation.y, player.onGround))

                if (ticksNeeded == 1 || player.capabilities.isCreativeMode) {
                    if (mode == Mode.PACKET) {
                        connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, blockPos, side))
                        player.swingArm(EnumHand.MAIN_HAND)
                        if (playSound) {
                            val soundType = initState.block.getSoundType(initState, world, blockPos, player)
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
                        playerController.onPlayerDestroyBlock(blockPos)
                        finish()
                    }
                } else {
                    timeout = ticksNeeded * 50L + 100L

                    playerController.onPlayerDamageBlock(blockPos, side)
                    player.swingArm(EnumHand.MAIN_HAND)
                    // cancel onPlayerDestroy NoGhostBlocks

//                    if (ticksNeeded * 50L < System.currentTimeMillis() - creationTime) {
//                        connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, blockPos, side))
//                        player.swingArm(EnumHand.MAIN_HAND)
//                        if (playSound) {
//                            val soundType = initState.block.getSoundType(initState, world, blockPos, player)
//                            world.playSound(
//                                player,
//                                blockPos,
//                                soundType.breakSound,
//                                SoundCategory.BLOCKS,
//                                (soundType.getVolume() + 1.0f) / 2.0f,
//                                soundType.getPitch() * 0.8f
//                            )
//                        }
//                    } else {
//                        player.swingArm(EnumHand.MAIN_HAND)
//                        if (playSound) {
//                            val soundType = initState.block.getSoundType(initState, world, blockPos, player)
//                            world.playSound(
//                                player,
//                                blockPos,
//                                soundType.hitSound,
//                                SoundCategory.BLOCKS,
//                                (soundType.getVolume() + 1.0f) / 2.0f,
//                                soundType.getPitch() * 0.8f
//                            )
//                        }
//                    }
                }


//                getHitVec(blockPos, side)
            }
        }

        safeListener<PacketEvent.PostReceive> {
            if (it.packet is SPacketBlockChange
                && it.packet.blockPosition == blockPos
                && it.packet.blockState.block == Blocks.AIR
            ) {
                finish()
            }
        }
    }

    private fun finish() {
        if (pickUpDrop) {
            color = ColorHolder(252, 3, 207)
            timeout = 10000L
            addSubActivities(
                PickUpDrops(initState.block.item),
                SetState(ActivityStatus.SUCCESS)
            )
        } else {
            activityStatus = ActivityStatus.SUCCESS
        }
    }
}