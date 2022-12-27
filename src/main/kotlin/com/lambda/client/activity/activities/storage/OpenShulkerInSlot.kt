package com.lambda.client.activity.activities.storage

import baritone.api.pathing.goals.GoalNear
import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.InstantActivity
import com.lambda.client.activity.activities.utils.getContainerPos
import com.lambda.client.activity.activities.interaction.OpenContainer
import com.lambda.client.activity.activities.interaction.PlaceBlock
import com.lambda.client.activity.activities.inventory.SwapOrSwitchToSlot
import com.lambda.client.activity.activities.travel.CustomGoal
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.block
import net.minecraft.inventory.Slot

class OpenShulkerInSlot(
    private val slot: Slot
) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        getContainerPos()?.let { containerPos ->
            addSubActivities(
                CustomGoal(GoalNear(containerPos, 3)),
                SwapOrSwitchToSlot(slot),
                PlaceBlock(containerPos, slot.stack.item.block),
                OpenContainer(containerPos)
            )
        }
    }
}