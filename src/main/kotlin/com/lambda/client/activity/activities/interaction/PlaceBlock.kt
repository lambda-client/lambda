package com.lambda.client.activity.activities.interaction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.inventory.CreativeInventoryAction
import com.lambda.client.activity.activities.travel.PlaceGoal
import com.lambda.client.activity.activities.types.AttemptActivity
import com.lambda.client.activity.activities.types.RenderAABBActivity
import com.lambda.client.activity.activities.types.RotatingActivity
import com.lambda.client.activity.activities.types.TimeoutActivity
import com.lambda.client.activity.activities.utils.Wait
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.module.modules.client.BuildTools.autoPathing
import com.lambda.client.module.modules.client.BuildTools.directionForce
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.items.block
import com.lambda.client.util.items.blockBlacklist
import com.lambda.client.util.items.item
import com.lambda.client.util.math.CoordinateConverter.asString
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getNeighbour
import com.lambda.client.util.world.isPlaceable
import net.minecraft.block.BlockColored
import net.minecraft.block.properties.PropertyDirection
import net.minecraft.block.state.IBlockState
import net.minecraft.item.EnumDyeColor
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.CPacketCreativeInventoryAction
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos

class PlaceBlock(
    private val blockPos: BlockPos,
    private val targetState: IBlockState,
    private val doPending: Boolean = false,
    private val ignoreDirection: Boolean = false,
    private val ignoreProperties: Boolean = false,
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

    private var spoofedDirection = false

    override fun SafeClientEvent.onInitialize() {
        if (world.getBlockState(blockPos) == targetState) {
            success()
            return
        }

        if (ignoreProperties && world.getBlockState(blockPos).block == targetState.block) {
            success()
            return
        }

        if (!world.isPlaceable(blockPos, targetState.getSelectedBoundingBox(world, blockPos))) {
            if (world.worldBorder.contains(blockPos)
                && !world.isOutsideBuildHeight(blockPos)) {
                addSubActivities(BreakBlock(blockPos))
            } else {
                failedWith(BlockOutsideOfWorldException(blockPos))
            }

            return
        }

        targetState.properties.entries.firstOrNull { it.key is PropertyDirection }?.let { entry ->
            val direction = (entry.value as EnumFacing).opposite

            if (directionForce
                && !ignoreDirection
                && !spoofedDirection
                && player.horizontalFacing != direction
            ) {
                addSubActivities(Rotate(Vec2f(direction.horizontalAngle, player.rotationPitch)))
                return
            }
        }

        targetState.properties.entries.firstOrNull { it.key == BlockColored.COLOR }?.let { entry ->
            val meta = (entry.value as EnumDyeColor).metadata

            if (player.getHeldItem(EnumHand.MAIN_HAND).metadata != meta) {
                if (!player.capabilities.isCreativeMode) {
                    addSubActivities(AcquireItemInActiveHand(targetState.block.item, metadata = meta, predicateItem = {
                        it.metadata == meta
                    }))
                    return
                }

                val stack = ItemStack(targetState.block.item, 1, meta)

                addSubActivities(CreativeInventoryAction(36 + player.inventory.currentItem, stack))
                return
            }
        }

        if (player.getHeldItem(EnumHand.MAIN_HAND).item.block != targetState.block) {
            if (!player.capabilities.isCreativeMode) {
                addSubActivities(AcquireItemInActiveHand(targetState.block.item))
                return
            }

            val stack = ItemStack(targetState.block.item)

            addSubActivities(CreativeInventoryAction(36 + player.inventory.currentItem, stack))
            return
        }

        getNeighbour(
            blockPos,
            attempts = BuildTools.placementSearch,
            visibleSideCheck = BuildTools.illegalPlacements,
            range = BuildTools.maxReach
        )?.let {
            val isBlacklisted = world.getBlockState(it.pos).block in blockBlacklist

            renderActivity.color = ColorHolder(11, 66, 89)

            if (isBlacklisted) {
                connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_SNEAKING))
            }

            rotation = getRotationTo(it.hitVec)

            val result = playerController.processRightClickBlock(player, world, it.pos, it.side, it.hitVec, EnumHand.MAIN_HAND)

            if (result != EnumActionResult.SUCCESS) {
                failedWith(ProcessRightClickException(result))
                return
            }

            player.swingArm(EnumHand.MAIN_HAND)

            if (isBlacklisted) {
                connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.STOP_SNEAKING))
            }

            if (doPending) {
                if (BuildTools.placeDelay == 0) {
                    owner.status = Status.PENDING
                } else {
                    addSubActivities(Wait(BuildTools.placeDelay * 50L))
                }
            }
        } ?: run {
            getNeighbour(
                blockPos,
                attempts = BuildTools.placementSearch,
                visibleSideCheck = false,
                range = 256f
            )?.let {
                if (autoPathing) addSubActivities(PlaceGoal(blockPos))
            } ?: run {
                failedWith(NoNeighbourException(blockPos))
            }
        }
    }

    init {
        safeListener<PacketEvent.PostReceive> {
            if (it.packet is SPacketBlockChange
                && it.packet.blockPosition == blockPos
                && subActivities.isEmpty()
            ) {
                when {
                    it.packet.blockState == targetState -> {
                        success()
                    }
                    ignoreProperties && it.packet.blockState.block == targetState.block -> {
                        success()
                    }
                    else -> {
                        failedWith(UnexpectedBlockStateException(blockPos, targetState, it.packet.blockState))
                    }
                }
            }
        }
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        when (childActivity) {
            is Wait -> {
                if (doPending) {
                    owner.status = Status.PENDING
                }
            }
            is Rotate -> {
                spoofedDirection = true
                status = Status.UNINITIALIZED
            }
            else -> {
                spoofedDirection = false
                status = Status.UNINITIALIZED
            }
        }
    }

    class NoNeighbourException(blockPos: BlockPos) : Exception("No neighbour for (${blockPos.asString()}) found")
    class ProcessRightClickException(result: EnumActionResult) : Exception("Processing right click failed with result $result")
    class UnexpectedBlockStateException(blockPos: BlockPos, expected: IBlockState, actual: IBlockState) : Exception("Unexpected block state at (${blockPos.asString()}) expected $expected but got $actual")
    class BlockOutsideOfWorldException(blockPos: BlockPos) : Exception("Block at (${blockPos.asString()}) is outside of world")
}