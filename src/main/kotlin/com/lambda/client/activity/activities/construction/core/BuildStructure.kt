package com.lambda.client.activity.activities.construction.core

import baritone.api.pathing.goals.Goal
import baritone.api.pathing.goals.GoalBlock
import baritone.api.pathing.goals.GoalInverted
import baritone.process.BuilderProcess
import com.lambda.client.activity.Activity
import com.lambda.client.activity.types.BuildActivity
import com.lambda.client.activity.types.RepeatingActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.module.modules.client.BuildTools.autoPathing
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.math.Direction
import com.lambda.client.util.math.VectorUtils.multiply
import com.lambda.client.util.threads.safeListener
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent

class BuildStructure(
    private val structure: Map<BlockPos, IBlockState>,
    private val direction: Direction = Direction.NORTH,
    private val offsetMove: BlockPos = BlockPos.ORIGIN,
    private val doPadding: Boolean = false,
    private val collectAll: Boolean = false,
    override val maximumRepeats: Int = 1,
    override var repeated: Int = 0,
) : RepeatingActivity, Activity() {
    private var currentOffset = BlockPos.ORIGIN
    private var lastGoal: Goal? = null

    override fun SafeClientEvent.onInitialize() {
        val activities = mutableListOf<Activity>()

        structure.forEach { (pos, targetState) ->
            getBuildActivity(pos.add(currentOffset), targetState)?.let { activities.add(it) }
        }

        addSubActivities(activities, subscribe = true)

        currentOffset = currentOffset.add(offsetMove)
    }

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.END) return@safeListener

            if (subActivities.isEmpty()) success()

            if (!autoPathing) return@safeListener

            when (val activity = getCurrentActivity()) {
                is PlaceBlock -> {
                    val blockPos = activity.blockPos

                    lastGoal = if (isInBlockAABB(blockPos)) {
                        GoalInverted(GoalBlock(blockPos))
                    } else {
                        BuilderProcess.GoalAdjacent(blockPos, blockPos, true)
                    }
                }
                is BreakBlock -> {
                    val blockPos = activity.blockPos

                    lastGoal = if (isInBlockAABB(blockPos.up())) {
                        GoalInverted(GoalBlock(blockPos.up()))
                    } else {
                        BuilderProcess.GoalBreak(blockPos)
                    }
                }
            }

            lastGoal?.let { goal ->
                BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(goal)
            }
        }

        /* Listen for any block changes like falling sand */
        safeListener<PacketEvent.PostReceive> { event ->
            if (event.packet !is SPacketBlockChange) return@safeListener

            val blockPos = event.packet.blockPosition

            structure[blockPos]?.let { targetState ->
                if (allSubActivities.any {
                    when (it) {
                        is BreakBlock -> it.blockPos == blockPos
                        is PlaceBlock -> it.blockPos == blockPos
                        else -> false
                    }
                }) return@safeListener

                getBuildActivity(blockPos, targetState)?.let {
                    addSubActivities(listOf(it), subscribe = true)
                }
            }
        }
    }

    private fun SafeClientEvent.getBuildActivity(blockPos: BlockPos, targetState: IBlockState): Activity? {
        val currentState = world.getBlockState(blockPos)

        when {
            /* is in padding */
            doPadding && isInPadding(blockPos) -> return null
            /* is in desired state */
            currentState == targetState -> return null
            /* block needs to be placed */
            targetState != Blocks.AIR.defaultState -> {
                return PlaceBlock(
                    blockPos, targetState
                )
            }
            /* block is not breakable */
            currentState.getBlockHardness(world, blockPos) < 0 -> return null
            /* only option left is breaking the block */
            else -> {
                return BreakBlock(
                    blockPos, collectDrops = collectAll, minCollectAmount = 64
                )
            }
        }
    }

    override fun getCurrentActivity(): Activity {
        subActivities.sortedWith(buildComparator()).firstOrNull()?.let {
            with(it) {
                return getCurrentActivity()
            }
        } ?: return this
    }

    fun buildComparator() = compareBy<Activity> {
        val current = deepestBuildActivity(it)

        if (current is BuildActivity) {
            current.context
        } else 0
    }.thenBy {
        val current = deepestBuildActivity(it)

        if (current is BuildActivity) {
            current.action
        } else 0
    }.thenBy {
        val current = deepestBuildActivity(it)

        if (current is BuildActivity) {
            current.distance
        } else 1337.0
    }

    /* BreakBlocks that are boxed in a PlaceBlock are considered in the sequence */
    private fun deepestBuildActivity(activity: Activity): Activity {
        activity.subActivities
            .filterIsInstance<BuildActivity>()
            .firstOrNull()?.let {
                return deepestBuildActivity(it as Activity)
        } ?: return activity
    }

    private fun SafeClientEvent.isInPadding(blockPos: BlockPos) = isBehindPos(player.flooredPosition, blockPos)

    private fun isBehindPos(origin: BlockPos, check: BlockPos): Boolean {
        val a = origin.add(direction.counterClockwise(2).directionVec.multiply(100))
        val b = origin.add(direction.clockwise(2).directionVec.multiply(100))

        return ((b.x - a.x) * (check.z - a.z) - (b.z - a.z) * (check.x - a.x)) > 0
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        when (childActivity) {
            is PlaceBlock -> {
                val blockPos = childActivity.blockPos
                val targetState = structure[blockPos] ?: return

                getBuildActivity(blockPos, targetState)?.let {
                    addSubActivities(it, subscribe = true)
                }
            }
            is BreakBlock -> {
                val blockPos = childActivity.blockPos
                val targetState = structure[blockPos] ?: return

                getBuildActivity(blockPos, targetState)?.let {
                    addSubActivities(it, subscribe = true)
                }
            }
        }
    }

    private fun SafeClientEvent.isInBlockAABB(blockPos: BlockPos) =
        !world.checkNoEntityCollision(AxisAlignedBB(blockPos), null)
}