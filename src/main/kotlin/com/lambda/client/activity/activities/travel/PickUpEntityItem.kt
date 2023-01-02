package com.lambda.client.activity.activities.travel

import baritone.api.pathing.goals.GoalBlock
import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.TimeoutActivity
import com.lambda.client.activity.activities.inventory.DumpSlot
import com.lambda.client.module.modules.player.InventoryManager
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.items.inventorySlots
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.item.EntityItem
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.lang.Exception

class PickUpEntityItem(
    private val entityItem: EntityItem,
    override val timeout: Long = 10000L
) : TimeoutActivity, Activity() {
    init {
        safeListener<TickEvent.ClientTickEvent> { event ->
            if (event.phase != TickEvent.Phase.START) return@safeListener

            if (!world.loadedEntityList.contains(entityItem)) {
                BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(null)
                success()
                return@safeListener
            }

            val emptySlots = player.inventory.mainInventory.filter { it.isEmpty }

            if (emptySlots.isNotEmpty()) {
                BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(GoalBlock(entityItem.position))
                return@safeListener
            }

            player.inventorySlots.firstOrNull { slot ->
                InventoryManager.ejectList.contains(slot.stack.item.registryName.toString())
            }?.let { slot ->
                addSubActivities(DumpSlot(slot))
            } ?: run {
                failedWith(InventoryFullException())
            }
        }
    }

    class InventoryFullException : Exception("No empty slots or items to dump!")
}