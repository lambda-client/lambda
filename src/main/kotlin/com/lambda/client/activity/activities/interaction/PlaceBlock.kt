package com.lambda.client.activity.activities.interaction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.travel.PlaceGoal
import com.lambda.client.activity.activities.types.AttemptActivity
import com.lambda.client.activity.activities.types.RenderAABBActivity
import com.lambda.client.activity.activities.types.RotatingActivity
import com.lambda.client.activity.activities.types.TimeoutActivity
import com.lambda.client.activity.activities.utils.Wait
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.items.block
import com.lambda.client.util.items.blockBlacklist
import com.lambda.client.util.items.item
import com.lambda.client.util.math.CoordinateConverter.asString
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getNeighbour
import com.lambda.client.util.world.toPlacePacket
import net.minecraft.block.state.IBlockState
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import java.lang.Exception

class PlaceBlock(
    private val blockPos: BlockPos,
    private val targetState: IBlockState, // TODO: Calculate correct resulting state of placed block to enable rotation checks
    private val doPending: Boolean = false,
    override var rotation: Vec2f = Vec2f.ZERO,
    override val timeout: Long = 200L,
    override val maxAttempts: Int = 8,
    override var usedAttempts: Int = 0,
    override val toRender: MutableSet<RenderAABBActivity.Companion.RenderAABBCompound> = mutableSetOf()
) : RotatingActivity, TimeoutActivity, AttemptActivity, RenderAABBActivity, Activity() {
    private val renderActivity = RenderAABBActivity.Companion.RenderBlockPos(
        blockPos,
        ColorHolder(35, 188, 254)
    ).also { toRender.add(it) }

    override fun SafeClientEvent.onInitialize() {
        if (world.getBlockState(blockPos).block == targetState.block) {
            success()
            return
        }

        if (player.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND).item.block != targetState.block) {
            addSubActivities(AcquireItemInActiveHand(targetState.block.item))
            return
        }

        getNeighbour(blockPos, attempts = 1, visibleSideCheck = true, range = 4.95f)?.let {
            val placedAtBlock = world.getBlockState(it.pos).block

            renderActivity.color = ColorHolder(11, 66, 89)

            if (placedAtBlock in blockBlacklist) {
                connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_SNEAKING))
            }

            rotation = getRotationTo(it.hitVec)

//            connection.sendPacket(it.toPlacePacket(EnumHand.MAIN_HAND))
            playerController.processRightClickBlock(player, world, it.pos, it.side, it.hitVec, EnumHand.MAIN_HAND)
            player.swingArm(EnumHand.MAIN_HAND)

            if (placedAtBlock in blockBlacklist) {
                connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.STOP_SNEAKING))
            }

            if (doPending) {
                addSubActivities(Wait(45L))
            }
        } ?: run {
            addSubActivities(PlaceGoal(blockPos))
//            failedWith(NoNeighbourException(blockPos))
        }
    }

    init {
        safeListener<PacketEvent.PostReceive> {
            if (it.packet is SPacketBlockChange
                && it.packet.blockPosition == blockPos
                && it.packet.blockState.block == targetState.block // TODO: Calculate correct resulting state of placed block to enable rotation checks
            ) {
                if (doPending) {
                    with(owner) {
                        success()
                    }
                }

                success()
            }
        }
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        when (childActivity) {
            is Wait -> if (doPending) owner.activityStatus = ActivityStatus.PENDING
            else -> {
//                activityStatus = ActivityStatus.UNINITIALIZED
            }
        }
    }

    class NoNeighbourException(blockPos: BlockPos) : Exception("No neighbour for (${blockPos.asString()}) found")
}