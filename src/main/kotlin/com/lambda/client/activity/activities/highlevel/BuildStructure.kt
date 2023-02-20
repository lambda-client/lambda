package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.interaction.BreakBlock
import com.lambda.client.activity.activities.interaction.PlaceBlock
import com.lambda.client.activity.activities.types.BuildActivity
import com.lambda.client.activity.activities.types.RepeatingActivity
import com.lambda.client.event.LambdaEventBus
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.ActivityManager.getCurrentActivity
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.math.Direction
import com.lambda.client.util.math.VectorUtils.multiply
import jdk.nashorn.internal.ir.annotations.Ignore
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.util.math.BlockPos

class BuildStructure(
    private val structure: Map<BlockPos, IBlockState>,
    private val direction: Direction = Direction.NORTH,
    private val offsetMove: BlockPos = BlockPos.ORIGIN,
    private val doPadding: Boolean = false,
    override val maximumRepeats: Int = 1,
    override var repeated: Int = 0,
) : RepeatingActivity, Activity() {
    private var currentOffset = BlockPos.ORIGIN

    override fun SafeClientEvent.onInitialize() {
        val activities = mutableListOf<Activity>()

        structure.forEach { (pos, targetState) ->
            val blockPos = pos.add(currentOffset)
            val currentState = world.getBlockState(blockPos)

            when {
                /* is in padding */
                doPadding && isInPadding(blockPos) -> return@forEach
                /* is in desired state */
                currentState == targetState -> return@forEach
                /* block needs to be placed */
                targetState != Blocks.AIR.defaultState -> {
                    activities.add(PlaceBlock(
                        blockPos, targetState
                    ))
                }
                /* only option left is breaking the block */
                else -> {
                    activities.add(BreakBlock(blockPos))
                }
            }
        }

        activities.forEach {
            LambdaEventBus.subscribe(it)
        }

        addSubActivities(activities)
    }

    override fun SafeClientEvent.onSuccess() {
        currentOffset = currentOffset.add(offsetMove)
    }

    override fun SafeClientEvent.getCurrentActivity(): Activity {
        subActivities
            .asSequence()
            .sortedWith(
                compareBy<Activity> {
                    with(it) {
                        getCurrentActivity().status
                    }
                }.thenBy {
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
                        player.getPositionEyes(1f).distanceTo(current.hitVec)
                    } else 0.0
                }
            ).firstOrNull()?.let {
                with(it) {
                    return getCurrentActivity()
                }
            } ?: return this@BuildStructure
    }

    /* BreakBlocks that are boxed in a PlaceBlock are considered in the sequence */
    private fun SafeClientEvent.deepestBuildActivity(activity: Activity): Activity {
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