package com.lambda.client.buildtools.blueprint.strategies

import com.lambda.client.buildtools.blueprint.BlueprintStrategy
import com.lambda.client.buildtools.task.TaskFactory
import com.lambda.client.util.math.Direction
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.math.VectorUtils.multiply
import com.sun.org.apache.xpath.internal.operations.Bool
import net.minecraft.util.math.BlockPos

class MoveXStrategy(
    private val originPos: BlockPos,
    val direction: Direction,
    val offset: Int,
    private val amount: Int,
    val hasStartPadding: Boolean = false
) : BlueprintStrategy {
    var distanceMoved = 0
    var currentOrigin = originPos

    override fun getNext(blueprint: HashMap<BlockPos, TaskFactory.BlueprintTask>): HashMap<BlockPos, TaskFactory.BlueprintTask> {
        distanceMoved++
        currentOrigin = originPos.add(direction.directionVec.multiply(offset * distanceMoved))

        val new = hashMapOf<BlockPos, TaskFactory.BlueprintTask>()

        blueprint.forEach {
            new[it.key.add(direction.directionVec.multiply(offset))] = it.value
        }

        return new
    }

    fun isInPadding(blockPos: BlockPos) = isBehindPos(originPos.add(direction.directionVec), blockPos)

    private fun isBehindPos(origin: BlockPos, check: BlockPos): Boolean {
        val a = origin.add(direction.counterClockwise(2).directionVec.multiply(100))
        val b = origin.add(direction.clockwise(2).directionVec.multiply(100))

        return ((b.x - a.x) * (check.z - a.z) - (b.z - a.z) * (check.x - a.x)) > 0
    }

    override fun getFinishMessage(): String {
        return "\n    §9> §7Actual distance: §a%,d§r".format(originPos.distanceTo(currentOrigin).toInt())
    }

    override fun isDone() = if (amount == 0) false else distanceMoved == amount
}
