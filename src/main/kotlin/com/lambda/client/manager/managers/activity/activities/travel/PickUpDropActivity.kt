package com.lambda.client.manager.managers.activity.activities.travel

import baritone.api.pathing.goals.GoalBlock
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.TimeoutActivity
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.gameevent.TickEvent

class PickUpDropActivity(
    private val item: Item,
    private val predicate: (ItemStack) -> Boolean = { true },
    private val maxRange: Float = 5.0f,
    override val timeout: Long = 10000L,
    override var creationTime: Long = 0L
) : TimeoutActivity, Activity() {
    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            allDrops().minByOrNull { drop -> player.distanceTo(drop.positionVector) }?.let { drop ->
                BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(GoalBlock(drop.position))
            } ?: run {
                activityStatus = ActivityStatus.SUCCESS
                BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(null)
            }
        }
    }

    private fun SafeClientEvent.allDrops() = world.loadedEntityList.filterIsInstance<EntityItem>().filter {
        it.item.item == item && player.distanceTo(it.positionVector) <= maxRange && predicate(it.item)
    }
}