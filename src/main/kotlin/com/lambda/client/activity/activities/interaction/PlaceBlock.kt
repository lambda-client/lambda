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
import net.minecraft.block.BlockGlazedTerracotta
import net.minecraft.block.BlockSlab
import net.minecraft.block.BlockSlab.EnumBlockHalf
import net.minecraft.block.properties.PropertyDirection
import net.minecraft.block.state.IBlockState
import net.minecraft.item.ItemStack
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
        /* check if is done */
        if (world.getBlockState(blockPos) == targetState) {
            success()
            return
        }

        /* less strict done check */
        if (ignoreProperties && world.getBlockState(blockPos).block == targetState.block) {
            success()
            return
        }

        /* check if block is placeable */
        if (!world.isPlaceable(blockPos, targetState.getSelectedBoundingBox(world, blockPos))) {
            if (world.worldBorder.contains(blockPos)
                && !world.isOutsideBuildHeight(blockPos)) {
                addSubActivities(BreakBlock(blockPos))
            } else {
                failedWith(BlockOutsideOfWorldException(blockPos))
            }

            return
        }

        /* rotate block to right direction */
        targetState.properties.entries.firstOrNull { it.key is PropertyDirection }?.let { entry ->
            var direction = entry.value as EnumFacing

            direction = when (targetState) { // ToDo: Exhaust all block types
                is BlockGlazedTerracotta -> direction.opposite
                else -> direction
            }

            if (directionForce
                && !ignoreDirection
                && !spoofedDirection
                && player.horizontalFacing != direction
            ) {
                addSubActivities(Rotate(Vec2f(direction.horizontalAngle, player.rotationPitch)))
                return
            }
        }

        /* half slab placement adjustments */
        var half: EnumBlockHalf? = null
        val allowedSides = EnumFacing.VALUES.toMutableList()

        targetState.properties.entries.firstOrNull { it.key == BlockSlab.HALF }?.let { entry ->
            half = entry.value as EnumBlockHalf

            when (half) {
                EnumBlockHalf.BOTTOM -> allowedSides.remove(EnumFacing.UP)
                EnumBlockHalf.TOP -> allowedSides.remove(EnumFacing.DOWN)
                else -> {}
            }
        }

        /* check if item has required metadata (declares the type) */
        val heldItem = player.getHeldItem(EnumHand.MAIN_HAND)
        val meta = targetState.block.getMetaFromState(targetState)

        if (heldItem.item.block != targetState.block || meta != heldItem.metadata) {
            if (!player.capabilities.isCreativeMode) {
                addSubActivities(AcquireItemInActiveHand(targetState.block.item, metadata = meta))
                return
            }

            val stack = ItemStack(targetState.block.item, 1, meta)

            addSubActivities(CreativeInventoryAction(36 + player.inventory.currentItem, stack))
            return
        }

        getNeighbour(
            blockPos,
            attempts = BuildTools.placementSearch,
            visibleSideCheck = BuildTools.illegalPlacements,
            range = BuildTools.maxReach,
            sides = allowedSides.toTypedArray()
        )?.let {
            var hitVec = it.hitVec

            half?.let { half ->
                if (it.side in EnumFacing.HORIZONTALS) {
                    hitVec = when (half) {
                        EnumBlockHalf.BOTTOM -> hitVec.add(0.0, -0.1, 0.0)
                        else -> hitVec.add(0.0, 0.1, 0.0)
                    }
                }
            }

            /* last check for placement state */
            val resultingState = targetState.block.getStateForPlacement(world, it.pos, it.side, hitVec.x.toFloat(), hitVec.y.toFloat(), hitVec.z.toFloat(), meta, player, EnumHand.MAIN_HAND)

            if (resultingState != targetState && !spoofedDirection) {
                failedWith(PlacementStateException(resultingState, targetState))
                return
            }

            val isBlacklisted = world.getBlockState(it.pos).block in blockBlacklist

            renderActivity.color = ColorHolder(11, 66, 89)

            if (isBlacklisted) {
                connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_SNEAKING))
            }

            rotation = getRotationTo(hitVec)

            val result = playerController.processRightClickBlock(player, world, it.pos, it.side, hitVec, EnumHand.MAIN_HAND)

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
    class PlacementStateException(placementState: IBlockState, targetState: IBlockState) : Exception("Placement state $placementState does not match target state $targetState")
    class UnexpectedBlockStateException(blockPos: BlockPos, expected: IBlockState, actual: IBlockState) : Exception("Unexpected block state at (${blockPos.asString()}) expected $expected but got $actual")
    class BlockOutsideOfWorldException(blockPos: BlockPos) : Exception("Block at (${blockPos.asString()}) is outside of world")
}