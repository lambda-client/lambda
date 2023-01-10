package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.RenderAABBActivity
import com.lambda.client.activity.activities.types.RepeatingActivity
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
    override val toRender: MutableSet<RenderAABBActivity.Companion.RenderAABBCompound> = mutableSetOf(),
    override val maximumRepeats: Int = 1,
    override var repeated: Int = 0,
) : RepeatingActivity, RenderAABBActivity, Activity() {
    private var currentOffset = BlockPos.ORIGIN

    override fun SafeClientEvent.onInitialize() {
//        addSubActivities(CustomGoal(GoalNear()))
        structure.asSequence().sortedBy { player.distanceTo(it.key) }.forEach { (pos, state) ->
            val offsetPos = pos.add(currentOffset)

            if (isInPadding(offsetPos)) return@forEach

            addSubActivities(
                BuildBlock(offsetPos, state)
            )
        }
    }

    override fun SafeClientEvent.onSuccess() {
        currentOffset = currentOffset.add(offsetMove)
    }

    private fun SafeClientEvent.isInPadding(blockPos: BlockPos) = isBehindPos(player.flooredPosition, blockPos)

    private fun isBehindPos(origin: BlockPos, check: BlockPos): Boolean {
        val a = origin.add(direction.counterClockwise(2).directionVec.multiply(100))
        val b = origin.add(direction.clockwise(2).directionVec.multiply(100))

        return ((b.x - a.x) * (check.z - a.z) - (b.z - a.z) * (check.x - a.x)) > 0
    }
}