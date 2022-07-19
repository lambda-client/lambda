package com.lambda.client.buildtools.pathfinding.strategies

import baritone.api.pathing.goals.GoalBlock
import baritone.api.process.PathingCommand
import baritone.api.process.PathingCommandType
import com.lambda.client.buildtools.pathfinding.MovementStrategy
import com.lambda.client.buildtools.pathfinding.Navigator
import com.lambda.client.buildtools.task.BuildTask
import com.lambda.client.buildtools.task.TaskProcessor.convertTo
import com.lambda.client.buildtools.task.build.DoneTask
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.util.EntityUtils.getDroppedItems
import com.lambda.client.util.items.firstByStack
import com.lambda.client.util.items.id
import com.lambda.client.util.items.inventorySlots
import com.lambda.client.util.items.throwAllInSlot
import com.lambda.client.util.math.VectorUtils
import com.lambda.client.util.world.isPlaceable
import com.lambda.client.util.world.isReplaceable
import net.minecraft.util.math.BlockPos

object PickupStrategy : MovementStrategy {
    override fun SafeClientEvent.generatePathingCommand(buildTask: BuildTask): PathingCommand {
        getCollectingPosition(buildTask.pickupItem.id)?.let { collectPos ->
            if (buildTask.timeTicking > 20 && player.inventorySlots.none { it.stack.isEmpty }) {
                player.inventorySlots.firstByStack { buildTask.itemIsFillerMaterial(it.item) }?.let {
                    throwAllInSlot(BuildTools, it)
                    buildTask.timeTicking = 0
                }
            }

            return PathingCommand(GoalBlock(collectPos), PathingCommandType.SET_GOAL_AND_PATH)
        }

        Navigator.reset()
        buildTask.convertTo<DoneTask>()
        return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
    }

    override fun onLostControl() {
        /* ignore */
    }

    private fun SafeClientEvent.getCollectingPosition(itemId: Int): BlockPos? {
        getDroppedItems(itemId, range = BuildTools.pickupRadius.toFloat())
            .minByOrNull { player.getDistance(it) }
            ?.positionVector
            ?.let { itemVec ->
                return VectorUtils.getBlockPosInSphere(itemVec, BuildTools.pickupRadius.toFloat()).asSequence()
                    .filter { pos ->
                        world.isAirBlock(pos.up())
                            && world.isPlaceable(pos)
                            && !world.getBlockState(pos.down()).isReplaceable
                    }
                    .sortedWith(
                        compareBy<BlockPos> {
                            it.distanceSqToCenter(itemVec.x, itemVec.y, itemVec.z)
                        }.thenBy {
                            it.y
                        }
                    ).firstOrNull()
            }
        return null
    }
}