package com.lambda.client.activity.activities.travel

import baritone.api.pathing.goals.GoalBlock
import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.TimeoutActivity
import com.lambda.client.activity.activities.inventory.DumpSlot
import com.lambda.client.module.modules.player.InventoryManager
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.items.inventorySlots
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.item.EntityItem
import net.minecraftforge.fml.common.gameevent.TickEvent

class PickUpEntityItem(
    entityItem: EntityItem,
    override val timeout: Long = 10000L,
    override var creationTime: Long = 0L
) : TimeoutActivity, Activity() {
    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            if (world.loadedEntityList.contains(entityItem)) {
                val emptySlots = player.inventory.mainInventory.filter { it.isEmpty }

                if (emptySlots.isNotEmpty()) {
                    BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(GoalBlock(entityItem.position))
                } else {
                    player.inventorySlots.firstOrNull { slot ->
                        InventoryManager.ejectList.contains(slot.stack.item.registryName.toString())
                    }?.let { slot ->
                        addSubActivities(DumpSlot(slot))
                    }
                }
            } else {
                activityStatus = ActivityStatus.SUCCESS
                BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(null)
            }
        }
    }
}