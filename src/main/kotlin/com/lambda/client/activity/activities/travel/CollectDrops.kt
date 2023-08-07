package com.lambda.client.activity.activities.travel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.types.LoopWhileActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.math.VectorUtils.distanceTo
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class CollectDrops(
    private val item: Item,
    private val itemStack: ItemStack = ItemStack.EMPTY,
    private val predicate: (ItemStack) -> Boolean = { true },
    private val maxRange: Float = 10.0f,
    private val minAmount: Int = 1,
    override var currentLoops: Int = 0,
) : LoopWhileActivity, Activity() {
    private val SafeClientEvent.drops
        get() =
            world.loadedEntityList.filterIsInstance<EntityItem>().filter {
                player.distanceTo(it.positionVector) < maxRange
                    && it.item.item == item
                    && predicate(it.item)
                    && if (itemStack != ItemStack.EMPTY) ItemStack.areItemStacksEqual(it.item, itemStack) else true
            }

    override val loopWhile: SafeClientEvent.() -> Boolean = {
        drops.isNotEmpty()
    }

    override fun SafeClientEvent.onInitialize() {
        if (drops.isEmpty() || drops.sumOf { it.item.count } < minAmount) {
            success()
            return
        }

        drops.minByOrNull { drop -> player.distanceTo(drop.positionVector) }?.let { drop ->
            addSubActivities(CollectEntityItem(drop))
        }
    }
}