package com.lambda.client.activity.activities.storage

import baritone.api.pathing.goals.GoalGetToBlock
import baritone.api.pathing.goals.GoalNear
import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.storage.core.CloseContainer
import com.lambda.client.activity.activities.storage.core.OpenContainer
import com.lambda.client.activity.activities.storage.core.ContainerTransaction
import com.lambda.client.activity.activities.travel.CustomGoal
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.misc.WorldEater
import com.lambda.client.util.math.VectorUtils.distanceTo
import net.minecraft.tileentity.TileEntity

/**
 * [StashTransaction] is an [Activity] that allows you to pull or push items from a [WorldEater.Stash].
 * @param stash The [WorldEater.Stash] to pull or push items from.
 * @param orders The orders to pull or push items.
 */
class StashTransaction(
    private val stash: WorldEater.Stash,
    private val orders: List<ContainerTransaction.Order>
) : Activity() {
    private val orderQueue = ArrayDeque(orders)
    private val containerQueue = ArrayDeque<TileEntity>()

    override fun SafeClientEvent.onInitialize() {
        if (orderQueue.isEmpty()) {
            success()
            return
        }

        //TODO: Use cached chests to instantly find the closest needed container (maybe even shared cache?)

        addSubActivities(
            CustomGoal(GoalNear(stash.area.center, stash.area.maxWidth), timeout = 999999L)
        )
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        when (childActivity) {
            is CustomGoal -> {
                if (childActivity.goal !is GoalNear) return

                val stashContainer = world.loadedTileEntityList.filter {
                    stash.area.containedBlockPositions.contains(it.pos)
                }

                if (stashContainer.isEmpty()) {
                    failedWith(NoContainerFoundInStashException())
                    return
                }

                containerQueue.addAll(
                    stashContainer.sortedBy { player.distanceTo(it.pos) }
                )

                executeOrdersOnContainer(containerQueue.removeFirst())
            }
            is CloseContainer -> {
                if (orderQueue.isEmpty()) {
                    success()
                    return
                }

                executeOrdersOnContainer(containerQueue.removeFirst())
            }
        }
    }

    private fun executeOrdersOnContainer(container: TileEntity) {
        addSubActivities(
            CustomGoal(GoalGetToBlock(container.pos)),
            OpenContainer(container.pos)
        )

        addSubActivities(orders.sortedBy { it.action }.map {
            ContainerTransaction(it)
        })

        orderQueue.clear() // TODO: Remove this when order feedback is implemented

        addSubActivities(CloseContainer())
    }

    class NoContainerFoundInStashException : Exception("No chest found in area!")
}