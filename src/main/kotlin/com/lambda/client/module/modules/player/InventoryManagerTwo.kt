package com.lambda.client.module.modules.player

import com.lambda.client.activity.activities.interaction.BreakBlock
import com.lambda.client.activity.activities.inventory.SwapOrSwitchToSlot
import com.lambda.client.activity.activities.storage.PlaceContainer
import com.lambda.client.event.events.GuiEvent
import com.lambda.client.event.events.WindowClickEvent
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.ActivityManager.addSubActivities
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.items.block
import com.lambda.client.util.items.item
import com.lambda.client.util.threads.safeListener
import net.minecraft.block.BlockShulkerBox
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.gui.inventory.GuiShulkerBox
import net.minecraft.init.Blocks
import net.minecraft.inventory.ClickType
import net.minecraft.item.ItemShulkerBox

object InventoryManagerTwo : Module(
    name = "InventoryManagerTwo",
    description = "Manages your inventory automatically",
    category = Category.PLAYER
) {
    private val placedShulkerBoxes = mutableListOf<PlaceContainer>()

    init {
        safeListener<WindowClickEvent> {
            if (it.type != ClickType.PICKUP || it.mouseButton != 1) return@safeListener

            player.openContainer.inventorySlots.getOrNull(it.slotId)?.let { slot ->
                if (!(slot.stack.item is ItemShulkerBox || slot.stack.item == Blocks.ENDER_CHEST.item)) return@safeListener

                val placeContainer = PlaceContainer(slot.stack.item.block.defaultState)

                placedShulkerBoxes.add(placeContainer)

                ActivityManager.addSubActivities(
                    SwapOrSwitchToSlot(slot),
                    placeContainer
                )
            }
        }

        safeListener<GuiEvent.Closed> {
            if (!(it.screen is GuiShulkerBox || it.screen is GuiChest)) return@safeListener

            placedShulkerBoxes.firstOrNull()?.let { placeContainerInSlot ->
                placedShulkerBoxes.remove(placeContainerInSlot)

                if (world.getBlockState(placeContainerInSlot.containerPos).block !is BlockShulkerBox) return@safeListener

                ActivityManager.addSubActivities(
                    BreakBlock(placeContainerInSlot.containerPos, collectDrops = true)
                )
            }
        }
    }
}