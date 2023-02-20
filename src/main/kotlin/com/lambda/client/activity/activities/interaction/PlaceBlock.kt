package com.lambda.client.activity.activities.interaction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.travel.PlaceGoal
import com.lambda.client.activity.activities.types.*
import com.lambda.client.activity.activities.utils.Wait
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.elements.client.ActivityManagerHud
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.module.modules.client.BuildTools.autoPathing
import com.lambda.client.module.modules.client.BuildTools.directionForce
import com.lambda.client.module.modules.client.BuildTools.placeStrictness
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.items.blockBlacklist
import com.lambda.client.util.math.CoordinateConverter.asString
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.PlaceInfo
import com.lambda.client.util.world.getNeighbour
import com.lambda.client.util.world.isPlaceable
import net.minecraft.block.*
import net.minecraft.block.state.IBlockState
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.IStringSerializable
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent

class PlaceBlock(
    private val blockPos: BlockPos,
    private val targetState: IBlockState,
    private val ignoreProperties: Boolean = false,
    private val ignoreFacing: Boolean = false,
    override var rotation: Vec2f? = null,
    override val timeout: Long = 200L,
    override val maxAttempts: Int = 8,
    override var usedAttempts: Int = 0,
    override val toRender: MutableSet<RenderAABBActivity.Companion.RenderAABBCompound> = mutableSetOf(),
    override var context: BuildActivity.BuildContext = BuildActivity.BuildContext.NONE,
    override var action: BuildActivity.BuildAction = BuildActivity.BuildAction.UNINIT,
    override var hitVec: Vec3d = Vec3d.ZERO
) : RotatingActivity, TimeoutActivity, AttemptActivity, RenderAABBActivity, BuildActivity, Activity() {
    private var placeInfo: PlaceInfo? = null
    private var spoofedDirection = false

    private val renderActivity = RenderAABBActivity.Companion.RenderBlockPos(
        blockPos,
        action.color
    ).also { toRender.add(it) }

    private enum class PlacementOffset(val offset: Vec3d) {
        UPPER(Vec3d(0.0, 0.1, 0.0)),
        CENTER(Vec3d.ZERO),
        LOWER(Vec3d(0.0, -0.1, 0.0))
    }

    private val blocksToOppositeDirection = listOf(
        BlockPistonBase::class,
        BlockEnderChest::class,
        BlockEndPortalFrame::class,
        BlockGlazedTerracotta::class,
        BlockPumpkin::class,
        BlockRedstoneDiode::class,
    )

    init {
        runSafe { updateState() }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            updateState()

            if (status != Status.RUNNING
                || context == BuildActivity.BuildContext.PENDING
                || subActivities.isNotEmpty()
            ) return@safeListener

            when (action) {
                BuildActivity.BuildAction.PLACE -> {
                    placeInfo?.let { placeInfo ->
                        checkPlace(placeInfo)
                    }
                }
                BuildActivity.BuildAction.WRONG_POS_PLACE -> {
                    if (autoPathing) addSubActivities(PlaceGoal(blockPos))
                }
                else -> {
                    // ToDo: place neighbours
                    failedWith(NoNeighbourException(blockPos))
                }
            }
        }
    }

    override fun SafeClientEvent.onInitialize() {
        updateState()

        placeInfo?.let {
            checkPlace(it)
        }
    }

    private fun SafeClientEvent.updateState() {
        val blockState = world.getBlockState(blockPos)
        if (blockState == targetState || (ignoreProperties && blockState.block == targetState.block)) {
            ActivityManagerHud.totalBlocksPlaced++
            success()
            return
        }

        val allowedSides = EnumFacing.VALUES.toMutableList()
        var placementOffset = PlacementOffset.CENTER

//        var allowedRotations = targetState.block.getValidRotations(world, blockPos)?.toMutableSet()

        /* check if block is placeable */
        if (!world.isPlaceable(blockPos, targetState.getSelectedBoundingBox(world, blockPos))) {
            if (world.worldBorder.contains(blockPos)
                && !world.isOutsideBuildHeight(blockPos)) {
                if (subActivities.isEmpty()) addSubActivities(BreakBlock(blockPos))
            } else {
                failedWith(BlockOutsideOfWorldException(blockPos))
            }
            return
        }

        targetState.properties.entries.firstOrNull { it.key.name == "facing" }?.let { entry ->
            val direction = entry.value as EnumFacing

            if (targetState.block is BlockButton) {
                allowedSides.clear()
                allowedSides.add(direction.opposite)
            }
        }

        targetState.properties.entries.firstOrNull { it.key.name == "half" }?.let { entry ->
            val half = entry.value as IStringSerializable
            placementOffset = when (half.name) {
                "top" -> {
                    allowedSides.remove(EnumFacing.DOWN)
                    PlacementOffset.UPPER
                }
                else -> {
                    allowedSides.remove(EnumFacing.UP)
                    PlacementOffset.LOWER
                }
            }
        }

        targetState.properties.entries.firstOrNull { it.key.name == "axis" }?.let { entry ->
            val axis = entry.value as IStringSerializable

            when (axis.name) {
                "x" -> allowedSides.removeIf { it.axis != EnumFacing.Axis.X }
                "y" -> allowedSides.removeIf { it.axis != EnumFacing.Axis.Y }
                "z" -> allowedSides.removeIf { it.axis != EnumFacing.Axis.Z }
                else -> {}
            }
        }

        /* quartz is special snowflake */
        targetState.properties.entries.firstOrNull { it.key.name == "variant" }?.let { entry ->
            when (entry.value) {
                BlockQuartz.EnumType.LINES_X -> allowedSides.removeIf { it.axis != EnumFacing.Axis.X }
                BlockQuartz.EnumType.LINES_Y -> allowedSides.removeIf { it.axis != EnumFacing.Axis.Y }
                BlockQuartz.EnumType.LINES_Z -> allowedSides.removeIf { it.axis != EnumFacing.Axis.Z }
                else -> {}
            }
        }

        getNeighbour(
            blockPos,
            attempts = BuildTools.placementSearch,
            visibleSideCheck = placeStrictness != BuildTools.PlacementStrictness.ANY,
            range = BuildTools.maxReach,
            sides = allowedSides.toTypedArray()
        )?.let {
            placeInfo = it
            action = BuildActivity.BuildAction.PLACE
            hitVec = it.hitVec.add(placementOffset.offset)
        } ?: run {
            getNeighbour(
                blockPos,
                attempts = BuildTools.placementSearch,
                visibleSideCheck = false,
                range = 256f,
                sides = allowedSides.toTypedArray()
            )?.let {
                action = BuildActivity.BuildAction.WRONG_POS_PLACE
                hitVec = it.hitVec.add(placementOffset.offset)
            } ?: run {
                action = BuildActivity.BuildAction.INVALID_PLACE
                hitVec = Vec3d.ZERO
            }
            placeInfo = null
            rotation = null
        }

        renderActivity.color = action.color
    }

    private fun SafeClientEvent.checkPlace(placeInfo: PlaceInfo) {
//        if (!targetState.block.canPlaceBlockOnSide(world, blockPos, EnumFacing.UP)) {
//            failedWith(BlockNotPlaceableException(blockPos))
//            return
//        }

        /* check if item has required metadata (declares the type) */
        val heldItemStack = player.getHeldItem(EnumHand.MAIN_HAND)
        val optimalStack = if (targetState.block is BlockShulkerBox) {
            ItemStack(targetState.block, 1, targetState.block.getMetaFromState(targetState))
        } else {
            @Suppress("DEPRECATION")
            targetState.block.getItem(world, blockPos, targetState)
        }

        if (heldItemStack.item != optimalStack.item
            || (!ignoreProperties && optimalStack.metadata != heldItemStack.metadata)
        ) {
            context = BuildActivity.BuildContext.RESTOCK

            addSubActivities(AcquireItemInActiveHand(
                optimalStack.item,
                metadata = optimalStack.metadata
            ))
            return
        }

        targetState.properties.entries.firstOrNull { it.key.name == "facing" }?.let { entry ->
            var direction = entry.value as EnumFacing

//            BlockDirectional
//            BlockHorizontal
//            BlockTorch
//            BlockLever

            if (targetState.block::class in blocksToOppositeDirection) direction = direction.opposite

            /* rotate block to right direction if possible */
            if (directionForce
                && !spoofedDirection
                && player.horizontalFacing != direction
            ) {
                addSubActivities(Rotate(Vec2f(direction.horizontalAngle, player.rotationPitch)))
                return
            }
        }

        /* last check for placement state */
//            val resultingState = targetState.block.getStateForPlacement(
//                world,
//                it.pos,
//                it.side,
//                hitVec.x.toFloat(), hitVec.y.toFloat(), hitVec.z.toFloat(),
//                heldItem.metadata,
//                player,
//                EnumHand.MAIN_HAND
//            )
//
//            if (resultingState != targetState
//                && !spoofedDirection
//                && targetState.block !is BlockButton // ToDo: find out why buttons don't work with this
//            ) {
//                failedWith(PlacementStateException(resultingState, targetState))
//                return
//            }

        doPlace(placeInfo)
    }

    private fun SafeClientEvent.doPlace(placeInfo: PlaceInfo) {
        val isBlacklisted = world.getBlockState(placeInfo.pos).block in blockBlacklist

        renderActivity.color = ColorHolder(11, 66, 89)

        if (isBlacklisted) {
            connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_SNEAKING))
        }

        rotation = getRotationTo(hitVec)

        val result = playerController.processRightClickBlock(player, world, placeInfo.pos, placeInfo.side, hitVec, EnumHand.MAIN_HAND)

        if (result != EnumActionResult.SUCCESS) {
            failedWith(ProcessRightClickException(result))
            return
        }

        player.swingArm(EnumHand.MAIN_HAND)

        if (isBlacklisted) {
            connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.STOP_SNEAKING))
        }

        if (BuildTools.placeDelay != 0) addSubActivities(Wait(BuildTools.placeDelay * 50L))

        context = BuildActivity.BuildContext.PENDING
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        when (childActivity) {
//            is Wait -> {
//                if (doPending) owner.status = Status.PENDING
//            }

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
    class BlockNotPlaceableException(targetState: IBlockState) : Exception("Block $targetState is not placeable")
    class ProcessRightClickException(result: EnumActionResult) : Exception("Processing right click failed with result $result")
    class UnexpectedBlockStateException(blockPos: BlockPos, expected: IBlockState, actual: IBlockState) : Exception("Unexpected block state at (${blockPos.asString()}) expected $expected but got $actual")
    class BlockOutsideOfWorldException(blockPos: BlockPos) : Exception("Block at (${blockPos.asString()}) is outside of world")
}