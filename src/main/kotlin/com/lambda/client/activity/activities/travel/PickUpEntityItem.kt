package com.lambda.client.activity.activities.travel

import baritone.api.pathing.goals.GoalBlock
import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.DumpSlot
import com.lambda.client.activity.activities.types.TimeoutActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.items.countEmpty
import com.lambda.client.util.items.inventorySlots
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.item.EntityItem
import net.minecraftforge.fml.common.gameevent.TickEvent

class PickUpEntityItem(
    private val entityItem: EntityItem,
    override val timeout: Long = 20000L
) : TimeoutActivity, Activity() {
    init {
        safeListener<TickEvent.ClientTickEvent> { event ->
            if (event.phase != TickEvent.Phase.START) return@safeListener

            if (!world.loadedEntityList.contains(entityItem)) {
                BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(null)
                success()
                return@safeListener
            }

            if (player.inventorySlots.countEmpty() > 0) {
                BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(GoalBlock(entityItem.position))
                return@safeListener
            }

            if (subActivities.filterIsInstance<DumpSlot>().isNotEmpty()) return@safeListener

            player.inventorySlots.firstOrNull { slot ->
                BuildTools.ejectList.contains(slot.stack.item.registryName.toString())
            }?.let { slot ->
                addSubActivities(DumpSlot(slot))
            }
        }
    }

    override fun SafeClientEvent.onFailure(exception: Exception): Boolean {
        if (exception !is TimeoutActivity.Companion.TimeoutException) return false

        onFailure(InventoryFullException())
        return true
    }

    class InventoryFullException : Exception("No empty slots or items to dump!")
}