package com.lambda.client.activity.activities.highlevel

import baritone.api.pathing.goals.GoalBlock
import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.LoopingAmountActivity
import com.lambda.client.activity.activities.travel.CustomGoal
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.math.Direction
import com.lambda.client.util.math.VectorUtils.multiply
import net.minecraft.block.Block
import net.minecraft.util.math.BlockPos

class BuildHighway(
    private val origin: BlockPos,
    private val direction: Direction,
    private val material: Block,
    override val maxLoops: Int = 100,
    override var currentLoops: Int = 0
) : LoopingAmountActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        val currentPos = origin.add(direction.directionVec.multiply(currentLoops + 1))

//        VectorUtils.getBlockPosInSphere(player.getPositionEyes(1f), 5.0f).forEach {
//            if (it != origin.down()) addSubActivities(
//                BuildBlock(it, Blocks.AIR.defaultState)
//            )
//        }

        addSubActivities(
            BuildBlock(currentPos.add(direction.clockwise(1).directionVec), material.defaultState),
            BuildBlock(currentPos.add(direction.counterClockwise(1).directionVec), material.defaultState),
            BuildBlock(currentPos.add(direction.clockwise(1).directionVec).up(), material.defaultState),
            BuildBlock(currentPos.add(direction.counterClockwise(1).directionVec).up(), material.defaultState),
            CustomGoal(GoalBlock(currentPos.add(direction.directionVec)))
        )
    }
}