package com.lambda.client.activity.activities.interaction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.AttemptActivity
import com.lambda.client.activity.activities.types.RotatingActivity
import com.lambda.client.activity.activities.types.TimeoutActivity
import com.lambda.client.activity.activities.travel.PickUpDrops
import com.lambda.client.activity.activities.types.RenderAABBActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.items.block
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getHitVec
import com.lambda.client.util.world.getMiningSide
import net.minecraft.block.material.Material
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.util.EnumHand
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*
import kotlin.Exception
import kotlin.math.ceil

class BreakBlockRaw(
    private val blockPos: BlockPos,
    private val playSound: Boolean = true,
    private val miningSpeedFactor: Float = 1.0f,
    private val collectDrops: Boolean = false,
    private val minCollectAmount: Int = 1,
    override var timeout: Long = 200L,
    override val maxAttempts: Int = 8,
    override var usedAttempts: Int = 0,
    override var renderAABB: AxisAlignedBB = AxisAlignedBB(blockPos),
    override var color: ColorHolder = ColorHolder(240, 222, 60),
    override var rotation: Vec2f = Vec2f.ZERO
) : TimeoutActivity, AttemptActivity, RotatingActivity, RenderAABBActivity, Activity() {
    private var ticksNeeded = 0
    private var initState = Blocks.AIR.defaultState
    private var drop: Item = Items.AIR

    override fun SafeClientEvent.onInitialize() {
        val currentState = world.getBlockState(blockPos)

        if (currentState.material == Material.AIR) {
            success()
            return
        }

        initState = currentState
        drop = currentState.block.getItemDropped(currentState, Random(), 0)
        renderAABB = currentState.getSelectedBoundingBox(world, blockPos)
        ticksNeeded = ceil((1 / currentState.getPlayerRelativeBlockHardness(player, world, blockPos)) * miningSpeedFactor).toInt()
        timeout = ticksNeeded * 50L + 2000L
    }

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            getMiningSide(blockPos)?.let { side ->
                rotation = getRotationTo(getHitVec(blockPos, side))
                renderAABB = world.getBlockState(blockPos).getSelectedBoundingBox(world, blockPos)

                playerController.onPlayerDamageBlock(blockPos, side)
                mc.effectRenderer.addBlockHitEffects(blockPos, side)
                player.swingArm(EnumHand.MAIN_HAND)

//                if (ticksNeeded == 1 || player.capabilities.isCreativeMode) {
//                    playerController.onPlayerDestroyBlock(blockPos)
//                    player.swingArm(EnumHand.MAIN_HAND)
//                } else {
//                    playerController.onPlayerDamageBlock(blockPos, side)
//                    player.swingArm(EnumHand.MAIN_HAND)
//                    // cancel onPlayerDestroy NoGhostBlocks
//
////                    if (ticksNeeded * 50L < System.currentTimeMillis() - creationTime) {
////                        connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, blockPos, side))
////                        player.swingArm(EnumHand.MAIN_HAND)
////                    } else {
////                        player.swingArm(EnumHand.MAIN_HAND)
////                    }
//                }
            } ?: run {
                failedWith(ExceptionNoSurfaceExposed())
            }
        }

        safeListener<PacketEvent.PostReceive> {
            if (it.packet is SPacketBlockChange
                && it.packet.blockPosition == blockPos
                && it.packet.blockState.block == Blocks.AIR
            ) {
                if (!collectDrops) {
                    success()
                    return@safeListener
                }

                color = ColorHolder(252, 3, 207)

                if (drop.block == Blocks.AIR) return@safeListener

                addSubActivities(
                    PickUpDrops(drop, minAmount = minCollectAmount)
                )
            }
        }
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        if (childActivity !is PickUpDrops) return

        success()
    }

    private fun SafeClientEvent.playSound() {
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

        if (playSound) {
            val soundType = initState.block.getSoundType(initState, world, blockPos, player)
            world.playSound(
                player,
                blockPos,
                soundType.hitSound,
                SoundCategory.BLOCKS,
                (soundType.getVolume() + 1.0f) / 2.0f,
                soundType.getPitch() * 0.8f
            )
        }
    }

    override fun SafeClientEvent.onFailure(exception: Exception): Boolean {
        playerController.resetBlockRemoving()
        return false
    }

    class ExceptionNoSurfaceExposed : Exception("No block surface exposed to player")
}