package com.lambda.client.activity.activities.storage

import baritone.api.pathing.goals.GoalGetToBlock
import baritone.api.pathing.goals.GoalNear
import baritone.api.pathing.goals.GoalXZ
import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.storage.core.CloseContainer
import com.lambda.client.activity.activities.storage.core.OpenContainer
import com.lambda.client.activity.activities.storage.core.PushItemsToContainer
import com.lambda.client.activity.activities.travel.CustomGoal
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.math.VectorUtils.distanceTo
import net.minecraft.item.Item
import net.minecraft.util.math.BlockPos

class StoreItemsToStash(val items: List<Item>) : Activity() {
    private var originalPosition: BlockPos = BlockPos.ORIGIN

    override fun SafeClientEvent.onInitialize() {
        originalPosition = player.flooredPosition

        addSubActivities(
            CustomGoal(GoalNear(BuildTools.storagePos1.value, 10), timeout = 999999L)
        )
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        when (childActivity) {
            is CustomGoal -> {
                if (childActivity.goal !is GoalNear) return

                val chestsInArea = world.loadedTileEntityList.filter {
                    BlockPos.getAllInBox(BuildTools.storagePos1.value, BuildTools.storagePos2.value).contains(it.pos)
                }

                val useChest = chestsInArea.minByOrNull { chest ->
                    player.distanceTo(chest.pos)
                } ?: return failedWith(NoChestFoundException())

                addSubActivities(
                    CustomGoal(GoalGetToBlock(useChest.pos)),
                    OpenContainer(useChest.pos)
                )

                items.forEach {
                    addSubActivities(PushItemsToContainer(it, 0), )
                }

                addSubActivities(CloseContainer())

                // replenish items

                addSubActivities(
                    CustomGoal(GoalXZ(originalPosition.x, originalPosition.z), timeout = 999999L)
                )
            }
        }
    }

    class NoChestFoundException : Exception("No chest found in area!")
}