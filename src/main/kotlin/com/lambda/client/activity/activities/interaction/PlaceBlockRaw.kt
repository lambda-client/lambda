package com.lambda.client.activity.activities.interaction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.AttemptActivity
import com.lambda.client.activity.activities.types.RenderAABBActivity
import com.lambda.client.activity.activities.types.RotatingActivity
import com.lambda.client.activity.activities.types.TimeoutActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.items.blockBlacklist
import com.lambda.client.util.math.CoordinateConverter.asString
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getNeighbour
import com.lambda.client.util.world.toPlacePacket
import net.minecraft.block.state.IBlockState
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.util.EnumHand
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import java.lang.Exception

class PlaceBlockRaw(
    private val blockPos: BlockPos,
    private val targetState: IBlockState, // TODO: Calculate correct resulting state of placed block to enable rotation checks
    private val playSound: Boolean = true,
    override var rotation: Vec2f = Vec2f.ZERO,
    override val timeout: Long = 1000L,
    override val maxAttempts: Int = 8,
    override var usedAttempts: Int = 0,
    override var renderAABB: AxisAlignedBB = AxisAlignedBB(blockPos),
    override var color: ColorHolder = ColorHolder(35, 188, 254)
) : RotatingActivity, TimeoutActivity, AttemptActivity, RenderAABBActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        getNeighbour(blockPos, attempts = 1, visibleSideCheck = true)?.let {
            val placedAtBlock = world.getBlockState(it.pos).block

            renderAABB = targetState.getSelectedBoundingBox(world, blockPos)

            if (placedAtBlock in blockBlacklist) {
                connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_SNEAKING))
            }

            rotation = getRotationTo(it.hitVec)

            connection.sendPacket(it.toPlacePacket(EnumHand.MAIN_HAND))
            player.swingArm(EnumHand.MAIN_HAND)

            if (placedAtBlock in blockBlacklist) {
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

//            activityStatus = ActivityStatus.PENDING
        } ?: run {
            failedWith(NoNeighbourException(blockPos))
        }
    }

    init {
        safeListener<PacketEvent.PostReceive> {
            if (it.packet is SPacketBlockChange
                && it.packet.blockPosition == blockPos
                && it.packet.blockState.block == targetState.block // TODO: Calculate correct resulting state of placed block to enable rotation checks
            ) success()
        }
    }

    class NoNeighbourException(blockPos: BlockPos) : Exception("No neighbour for (${blockPos.asString()}) found")
}