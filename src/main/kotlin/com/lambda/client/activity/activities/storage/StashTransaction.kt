package com.lambda.client.activity.activities.storage

import baritone.api.pathing.goals.GoalBlock
import baritone.api.pathing.goals.GoalGetToBlock
import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.storage.core.CloseContainer
import com.lambda.client.activity.activities.storage.core.ContainerWindowTransaction
import com.lambda.client.activity.activities.storage.core.OpenContainer
import com.lambda.client.activity.activities.storage.types.ContainerAction
import com.lambda.client.activity.activities.storage.types.Stash
import com.lambda.client.activity.activities.storage.types.StackSelection
import com.lambda.client.activity.activities.travel.CustomGoal
import com.lambda.client.activity.types.LoopWhileActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.math.CoordinateConverter.asString
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.text.MessageSendHelper
import net.minecraft.tileentity.TileEntity

/**
 * [StashTransaction] is an [Activity] that allows you to pull or push items from a [Stash].
 * @param orders The orders to pull or push items.
 */
class StashTransaction(
    private val orders: Set<Triple<Stash, ContainerAction, StackSelection>>
) : LoopWhileActivity, Activity() {
    override val loopWhile: SafeClientEvent.() -> Boolean = { orderQueue.isNotEmpty() }
    override var currentLoops = 0

    private val orderQueue = ArrayDeque(orders.sortedBy { it.second })
    // try different containers in case one is not matching the search
    private val containerQueue = ArrayDeque<TileEntity>()

    override fun SafeClientEvent.onInitialize() {
        if (orderQueue.isEmpty()) {
            success()
            return
        }

        // TODO: Use cached chests to instantly find the closest needed container (maybe even shared cache?)

        orderQueue.firstOrNull()?.first?.let {
            val highestNonAirBlock = world.getTopSolidOrLiquidBlock(it.area.center)

            MessageSendHelper.sendWarningMessage("Player is not in the area ${it.area}! Moving to closest position (${highestNonAirBlock.asString()})...")
            addSubActivities(CustomGoal(GoalBlock(highestNonAirBlock),
                inGoal = { blockPos -> it.area.containedBlocks.contains(blockPos) }, timeout = 999999L)
            )
        }
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        when (childActivity) {
            is CustomGoal -> {
                if (childActivity.goal !is GoalBlock) return

                orderQueue.removeFirstOrNull()?.let { order ->
                    val stashContainer = world.loadedTileEntityList.filter {
                        order.first.area.containedBlocks.contains(it.pos)
                    }

                    if (stashContainer.isEmpty()) {
                        failedWith(NoContainerFoundInStashException())
                        return
                    }

                    stashContainer.minByOrNull { player.distanceTo(it.pos) }?.let { container ->
                        addSubActivities(
                            CustomGoal(GoalGetToBlock(container.pos)),
                            OpenContainer(container.pos),
                            ContainerWindowTransaction(order.second, order.third),
                            CloseContainer()
                        )
                    }
                }
            }
        }
    }

    class NoContainerFoundInStashException : Exception("No chest found in area!")
}