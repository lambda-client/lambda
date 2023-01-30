package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.RenderAABBActivity
import com.lambda.client.activity.activities.types.RepeatingActivity
import com.lambda.client.event.LambdaEventBus
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.math.Direction
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.math.VectorUtils.multiply
import net.minecraft.block.state.IBlockState
import net.minecraft.util.math.BlockPos

class BuildStructure(
    private val structure: Map<BlockPos, IBlockState>,
    private val direction: Direction = Direction.NORTH,
    private val offsetMove: BlockPos = BlockPos.ORIGIN,
    private val doPadding: Boolean = false,
    private val respectIgnore: Boolean = false,
    override val toRender: MutableSet<RenderAABBActivity.Companion.RenderAABBCompound> = mutableSetOf(),
    override val maximumRepeats: Int = 1,
    override var repeated: Int = 0,
) : RepeatingActivity, RenderAABBActivity, Activity() {
    private var currentOffset = BlockPos.ORIGIN

    override fun SafeClientEvent.onInitialize() {
        structure.forEach { (pos, state) ->
            val offsetPos = pos.add(currentOffset)

            if (doPadding && isInPadding(offsetPos)) return@forEach
            if (world.getBlockState(offsetPos) == state) return@forEach

            val activity = BuildBlock(offsetPos, state, respectIgnore)

            addSubActivities(activity)

            LambdaEventBus.subscribe(activity)
        }
    }

    override fun SafeClientEvent.onSuccess() {
        currentOffset = currentOffset.add(offsetMove)
    }

    override fun SafeClientEvent.getCurrentActivity(): Activity {
        subActivities
            .asSequence()
            .filterIsInstance<BuildBlock>()
            .sortedWith(
                compareBy<BuildBlock> {
                    it.status
                }.thenBy {
                    it.context
                }.thenBy {
                    it.action
                }.thenBy {
                    player.getPositionEyes(1f).distanceTo(it.blockPos)
                }
            ).firstOrNull()?.let {
                with(it) {
                    return getCurrentActivity()
                }
            } ?: return this@BuildStructure
    }

    private fun SafeClientEvent.isInPadding(blockPos: BlockPos) = isBehindPos(player.flooredPosition, blockPos)

    private fun isBehindPos(origin: BlockPos, check: BlockPos): Boolean {
        val a = origin.add(direction.counterClockwise(2).directionVec.multiply(100))
        val b = origin.add(direction.clockwise(2).directionVec.multiply(100))

        return ((b.x - a.x) * (check.z - a.z) - (b.z - a.z) * (check.x - a.x)) > 0
    }
}