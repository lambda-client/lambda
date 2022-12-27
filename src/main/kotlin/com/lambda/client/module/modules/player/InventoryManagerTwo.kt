package com.lambda.client.module.modules.player

import baritone.api.pathing.goals.GoalNear
import com.lambda.client.activity.activities.utils.getContainerPos
import com.lambda.client.activity.activities.interaction.BreakBlock
import com.lambda.client.activity.activities.interaction.OpenContainer
import com.lambda.client.activity.activities.interaction.PlaceBlock
import com.lambda.client.activity.activities.inventory.SwapOrSwitchToSlot
import com.lambda.client.activity.activities.inventory.SwapToBestTool
import com.lambda.client.activity.activities.travel.CustomGoal
import com.lambda.client.event.events.GuiEvent
import com.lambda.client.event.events.WindowClickEvent
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.ActivityManager.addSubActivities
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.items.block
import com.lambda.client.util.items.item
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.gui.inventory.GuiShulkerBox
import net.minecraft.init.Blocks
import net.minecraft.inventory.ClickType
import net.minecraft.item.ItemShulkerBox
import net.minecraft.util.math.BlockPos

object InventoryManagerTwo : Module(
    name = "InventoryManagerTwo",
    description = "Manages your inventory automatically",
    category = Category.PLAYER
) {
    private val placedShulkerBoxes = mutableListOf<BlockPos>()

    init {
        safeListener<WindowClickEvent> {
            if (it.type != ClickType.PICKUP) return@safeListener

            player.openContainer.inventorySlots.getOrNull(it.slotId)?.let { slot ->
                if (!(slot.stack.item is ItemShulkerBox || slot.stack.item == Blocks.ENDER_CHEST.item)) return@safeListener

                getContainerPos()?.let { containerPos ->
                    placedShulkerBoxes.add(containerPos)
                    ActivityManager.addSubActivities(
                        CustomGoal(GoalNear(containerPos, 3)),
                        SwapOrSwitchToSlot(slot),
                        PlaceBlock(containerPos, slot.stack.item.block),
                        OpenContainer(containerPos)
                    )
                }

                it.cancel()
            }
        }

        safeListener<GuiEvent.Closed> {
            if (!(it.screen is GuiShulkerBox || it.screen is GuiChest)) return@safeListener

            placedShulkerBoxes.firstOrNull()?.let { containerPos ->
                ActivityManager.addSubActivities(
                    CustomGoal(GoalNear(containerPos, 3)),
                    SwapToBestTool(containerPos),
                    BreakBlock(
                        containerPos,
                        pickUpDrop = true
                    )
                )
                placedShulkerBoxes.remove(containerPos)
            }
        }
    }
}