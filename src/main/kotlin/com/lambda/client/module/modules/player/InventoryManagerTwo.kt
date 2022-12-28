package com.lambda.client.module.modules.player

import com.lambda.client.activity.activities.utils.getContainerPos
import com.lambda.client.activity.activities.storage.BreakAndCollectShulker
import com.lambda.client.activity.activities.storage.OpenContainerInSlot
import com.lambda.client.event.events.GuiEvent
import com.lambda.client.event.events.WindowClickEvent
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.ActivityManager.addSubActivities
import com.lambda.client.module.Category
import com.lambda.client.module.Module
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
                        OpenContainerInSlot(slot)
                    )
                }

                it.cancel()
            }
        }

        safeListener<GuiEvent.Closed> {
            if (!(it.screen is GuiShulkerBox || it.screen is GuiChest)) return@safeListener

            placedShulkerBoxes.firstOrNull()?.let { containerPos ->
                ActivityManager.addSubActivities(
                    BreakAndCollectShulker(containerPos)
                )
                placedShulkerBoxes.remove(containerPos)
            }
        }
    }
}