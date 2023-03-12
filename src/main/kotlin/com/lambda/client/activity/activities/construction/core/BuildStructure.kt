package com.lambda.client.activity.activities.construction.core

import com.lambda.client.activity.Activity
import com.lambda.client.activity.types.BuildActivity
import com.lambda.client.activity.types.RepeatingActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.math.Direction
import com.lambda.client.util.math.VectorUtils.multiply
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.util.math.BlockPos

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

    override fun SafeClientEvent.onInitialize() {
        val activities = mutableListOf<Activity>()

        structure.forEach { (pos, targetState) ->
            getBuildActivity(pos.add(currentOffset), targetState)?.let { activities.add(it) }
        }

        addSubActivities(activities, subscribe = true)

        currentOffset = currentOffset.add(offsetMove)
    }

    init {
//        safeListener<TickEvent.ClientTickEvent> {
//            if (subActivities.isEmpty() || status == Status.UNINITIALIZED) return@safeListener
//            success()
//        }

//        /* Listen for any block changes like falling sand */
//        safeListener<PacketEvent.PostReceive> { event ->
//            if (event.packet !is SPacketBlockChange) return@safeListener
//
//            val blockPos = event.packet.blockPosition
//
//            structure[blockPos]?.let { targetState ->
//                val isContained = allSubActivities.none {
//                    when (it) {
//                        is BreakBlock -> it.blockPos == blockPos
//                        is PlaceBlock -> it.blockPos == blockPos
//                        else -> false
//                    }
//                }
//
//                if (isContained) return@safeListener
//
//                getBuildActivity(blockPos, targetState)?.let {
//                    addSubActivities(listOf(it), subscribe = true)
//                }
//            }
//        }
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
}