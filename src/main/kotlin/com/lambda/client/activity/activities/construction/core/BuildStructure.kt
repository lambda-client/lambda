package com.lambda.client.activity.activities.construction.core

import baritone.api.pathing.goals.Goal
import baritone.api.pathing.goals.GoalBlock
import baritone.api.pathing.goals.GoalInverted
import baritone.api.pathing.goals.GoalXZ
import baritone.process.BuilderProcess.GoalAdjacent
import com.lambda.client.LambdaMod
import com.lambda.client.activity.Activity
import com.lambda.client.activity.types.BuildActivity
import com.lambda.client.activity.types.RenderAABBActivity
import com.lambda.client.activity.types.RepeatingActivity
import com.lambda.client.commons.extension.floorToInt
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.module.modules.client.BuildTools.autoPathing
import com.lambda.client.module.modules.misc.WorldEater
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.items.block
import com.lambda.client.util.items.filterByStack
import com.lambda.client.util.items.inventorySlots
import com.lambda.client.util.math.Direction
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.math.VectorUtils.multiply
import com.lambda.client.util.threads.safeListener
import net.minecraft.block.BlockBush
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.item.EntityItem
import net.minecraft.init.Blocks
import net.minecraft.item.ItemShulkerBox
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.network.play.server.SPacketMultiBlockChange
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraftforge.event.world.BlockEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.properties.Delegates

class BuildStructure(
    private val structure: Map<BlockPos, IBlockState>,
    private val direction: Direction = Direction.NORTH,
    private val offsetMove: BlockPos = BlockPos.ORIGIN,
    private val doPadding: Boolean = false,
    private val collectAll: Boolean = false,
    private val breakBushes: Boolean = false,
    private val allowBreakDescend: Boolean = false,
    override val maximumRepeats: Int = 1,
    override var repeated: Int = 0,
    override val aabbCompounds: MutableSet<RenderAABBActivity.Companion.RenderAABBCompound> = mutableSetOf()
) : RepeatingActivity, RenderAABBActivity, Activity() {
    private var currentOffset = BlockPos.ORIGIN
    private var currentGoal: Goal? by Delegates.observable(null) { _, old, new ->
        if (old != new) lastGoalSet = System.currentTimeMillis()
    }
    private var lastGoalSet: Long = 0L

    private val renderAABB = RenderAABBActivity.Companion.RenderAABB(
        AxisAlignedBB(BlockPos.ORIGIN), ColorHolder(255, 255, 255)
    ).also { aabbCompounds.add(it) }

    override fun SafeClientEvent.onInitialize() {
        structure.forEach { (pos, targetState) ->
            createBuildActivity(pos.add(currentOffset), targetState)
        }

        currentOffset = currentOffset.add(offsetMove)

        if (subActivities.isEmpty()) success()
    }

    private fun SafeClientEvent.withinRangeOfStructure(): Boolean {
        return nearestStructureBlock()?.let { it.add(currentOffset).distanceTo(player.position) <= 5 } ?: true
    }

    private fun SafeClientEvent.nearestStructureBlock(): BlockPos? {
        return structure.keys.minByOrNull { it.add(currentOffset).distanceTo(player.position) }
    }

    init {
        safeListener<TickEvent.ClientTickEvent> { event ->
            if (event.phase != TickEvent.Phase.END) return@safeListener

            if (subActivities.isEmpty() && !BaritoneUtils.isPathing) {
                // todo: offset pathing like this might not make sense for all structures. we aren't guaranteed to be at a specific position relative to the structure
                //  we could path to the middle of the structure regardless of shape, but that will be inefficient. also structures are not guaranteed to be small/within render distance
                if (autoPathing && !withinRangeOfStructure() && offsetMove != BlockPos.ORIGIN) {
                    LambdaMod.LOG.info("Structure out of range, pathing by offset")
                    // todo: improve stop/start stutter pathing
                    BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(
                        GoalXZ(player.posX.floorToInt() + (offsetMove.x * 5), player.posZ.floorToInt() + (offsetMove.z * 5))
                    )
                    return@safeListener
                }
                success()
            }

            val activity = ActivityManager.getCurrentActivity()

            // no forced moving on other activities than native build activity
            if (activity !is BuildActivity || !activity.hasNoSubActivities || activity.context != BuildActivity.Context.NONE) return@safeListener

            // pathing cool-down
            if (System.currentTimeMillis() - lastGoalSet < BuildTools.pathingRecomputeTimeout) {
                BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(currentGoal)
                return@safeListener
            }

            // collect all shulker boxes just to be sure
            world.loadedEntityList.filterIsInstance<EntityItem>().firstOrNull {
                it.item.item is ItemShulkerBox
            }?.let {
                currentGoal = GoalBlock(it.position)
                return@safeListener
            }

            val itemsInRange = world.loadedEntityList
                .filterIsInstance<EntityItem>()
                .filter {
                    player.distanceTo(it.position) <= BuildTools.collectRange
                        && it.item.item in WorldEater.collectables
                }

            // collect drops
            if (collectAll && autoPathing
                && (itemsInRange.maxOfOrNull { it.item.count } ?: 0) > BuildTools.minimumStackSize
            ) {
                itemsInRange.maxByOrNull { it.item.count / player.distanceTo(it.position) }?.let { largestStack ->
                    currentGoal = GoalBlock(largestStack.position)
                    return@safeListener
                }
            }

            // move to next activity
            when (activity) {
                is PlaceBlock -> {
                    val blockPos = activity.blockPos

                    renderAABB.renderAABB = AxisAlignedBB(blockPos).grow(0.1)

                    if (!autoPathing) return@safeListener

                    currentGoal = if (isInBlockAABB(blockPos)) {
                        GoalInverted(GoalBlock(blockPos))
                    } else {
                        GoalAdjacent(blockPos, blockPos, true)
                    }
                }

                is BreakBlock -> {
                    val blockPos = activity.blockPos

                    renderAABB.renderAABB = AxisAlignedBB(blockPos).grow(0.1)

                    if (!autoPathing) return@safeListener

                    currentGoal = if (!allowBreakDescend && isInBlockAABB(blockPos.up())) {
                        GoalInverted(GoalBlock(blockPos.up()))
                    } else {
                        GoalAdjacent(blockPos, blockPos, true)
                    }
                }
            }

            currentGoal?.let { goal ->
                BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(goal)
            }
        }

//        safeListener<BlockEvent.EntityPlaceEvent> {  }

        /* Listen for any block changes like falling sand */
        safeListener<BlockEvent.NeighborNotifyEvent> { event ->
            handleBlockUpdate(event.pos, event.state)
        }

        safeListener<PacketEvent.PostReceive> {
            if (it.packet is SPacketBlockChange) {
                handleBlockUpdate(it.packet.blockPosition, it.packet.blockState)
            } else if (it.packet is SPacketMultiBlockChange) {
                it.packet.changedBlocks.forEach { blockData ->
                    handleBlockUpdate(blockData.pos, blockData.blockState)
                }
            }
        }
    }

    private fun SafeClientEvent.handleBlockUpdate(blockPos: BlockPos, blockState: IBlockState) {
        // todo: capture baritone placing support blocks and update structure
        structure[blockPos]?.let { targetState ->
            if (allSubActivities.any {
                    when (it) {
                        is BreakBlock -> it.blockPos == blockPos && targetState == blockState
                        is PlaceBlock -> it.blockPos == blockPos && targetState == blockState
                        else -> false
                    }
                }) return
//            MessageSendHelper.sendWarningMessage("Block changed at $blockPos")
            createBuildActivity(blockPos, targetState)
        }
    }

    private fun SafeClientEvent.createBuildActivity(blockPos: BlockPos, targetState: IBlockState) {
        val currentState = world.getBlockState(blockPos)

        /* is in padding */
        if (doPadding && isInPadding(blockPos)) return

        /* is in desired state */
        if (currentState == targetState) return

        /* block needs to be placed */
        if (targetState != Blocks.AIR.defaultState) {
            addSubActivities(PlaceBlock(
                blockPos, targetState
            ), subscribe = true)
            return
        }

        /* block is not breakable */
        if (currentState.getBlockHardness(world, blockPos) < 0) return

        /* block is auto breakable like lily-pad or tall grass */
        if (!breakBushes && currentState.block is BlockBush) return

        /* block should be ignored */
        if (currentState.block in BuildTools.ignoredBlocks) return

        /* the only option left is breaking the block */
        addSubActivities(BreakBlock(
//            blockPos, collectDrops = collectAll, minCollectAmount = 64
            blockPos
        ), subscribe = true)
    }

    override fun getCurrentActivity(): Activity {
        subActivities
            .filterIsInstance<BuildActivity>()
            .sortedWith(buildComparator())
            .firstOrNull()?.let { buildActivity ->
                (buildActivity as? Activity)?.let {
                    with(it) {
                        return getCurrentActivity()
                    }
                }
            } ?: return this
    }

    /**
     * Find out more about task sorting [here](https://docs.google.com/spreadsheets/d/1oZWV4qNu3Gao-7w3X_v5pjzRcXWjDhURMCKU25IIKEE/edit?usp=sharing)
     */
    fun buildComparator() = compareBy<BuildActivity> {
        it.context
    }.thenBy {
        it.availability
    }.thenBy {
        it.type
    }.thenByDescending {
        it.exposedSides
    }.thenBy {
        it.distance
    }

    private fun SafeClientEvent.isInPadding(blockPos: BlockPos) = isBehindPos(player.flooredPosition, blockPos)

    private fun isBehindPos(origin: BlockPos, check: BlockPos): Boolean {
        val a = origin.add(direction.counterClockwise(2).directionVec.multiply(100))
        val b = origin.add(direction.clockwise(2).directionVec.multiply(100))

        return ((b.x - a.x) * (check.z - a.z) - (b.z - a.z) * (check.x - a.x)) > 0
    }

    fun SafeClientEvent.addLiquidFill(liquidPos: BlockPos) {
        var exists = false

        subActivities
            .filterIsInstance<PlaceBlock>()
            .filter { it.blockPos == liquidPos }.forEach {
                it.type = BuildActivity.Type.LIQUID_FILL
                exists = true
            }

        if (exists) return

        val available = player.inventorySlots
            .filterByStack { BuildTools.ejectList.contains(it.item.block.registryName.toString()) }
            .maxByOrNull { it.stack.count }?.stack?.item?.block ?: Blocks.AIR

        if (available == Blocks.AIR) {
            failedWith(BreakBlock.NoFillerMaterialFoundException())
            return
        }

        val activity = PlaceBlock(liquidPos, available.defaultState)

        activity.type = BuildActivity.Type.LIQUID_FILL

        addSubActivities(activity)
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        when (childActivity) {
            is PlaceBlock -> {
                val blockPos = childActivity.blockPos
                val targetState = structure[blockPos] ?: return

                createBuildActivity(blockPos, targetState)
            }

            is BreakBlock -> {
                val blockPos = childActivity.blockPos
                val targetState = structure[blockPos] ?: return

                createBuildActivity(blockPos, targetState)
            }
        }
    }

    private fun SafeClientEvent.isInBlockAABB(blockPos: BlockPos) =
        !world.checkNoEntityCollision(AxisAlignedBB(blockPos), null)
}