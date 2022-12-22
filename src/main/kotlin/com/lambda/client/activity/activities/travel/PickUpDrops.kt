package com.lambda.client.activity.activities.travel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.InstantActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.math.VectorUtils.distanceTo
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class PickUpDrops(
    private val item: Item,
    private val predicate: (ItemStack) -> Boolean = { true },
    private val maxRange: Float = 10.0f
) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        world.loadedEntityList.filterIsInstance<EntityItem>().filter {
            it.item.item == item && player.distanceTo(it.positionVector) < maxRange && predicate(it.item)
        }.sortedBy { drop -> player.distanceTo(drop.positionVector) }.forEach { drop ->
            addSubActivities(PickUpEntityItem(drop))
        }
    }
}