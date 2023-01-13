package com.lambda.client.module.modules.player

import com.lambda.client.activity.activities.interaction.BreakBlock
import com.lambda.client.activity.activities.storage.OpenShulkerFromSlot
import com.lambda.client.event.events.WindowClickEvent
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.ActivityManager.addSubActivities
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.items.item
import com.lambda.client.util.threads.safeListener
import net.minecraft.block.BlockEnderChest
import net.minecraft.block.BlockShulkerBox
import net.minecraft.init.Blocks
import net.minecraft.inventory.ClickType
import net.minecraft.item.ItemShulkerBox
import net.minecraftforge.fml.common.gameevent.TickEvent

object InventoryManagerTwo : Module(
    name = "InventoryManagerTwo",
    description = "Manages your inventory automatically",
    category = Category.PLAYER
) {
    private val placedShulkerBoxes = ArrayDeque<OpenShulkerFromSlot>(mutableListOf())

    init {
        safeListener<WindowClickEvent> {
            if (it.type != ClickType.PICKUP || it.mouseButton != 1) return@safeListener

            player.openContainer.inventorySlots.getOrNull(it.slotId)?.let { slot ->
                if (!(slot.stack.item is ItemShulkerBox || slot.stack.item == Blocks.ENDER_CHEST.item)) return@safeListener

                val openShulkerFromSlot = OpenShulkerFromSlot(slot)

                placedShulkerBoxes.add(openShulkerFromSlot)

                ActivityManager.addSubActivities(openShulkerFromSlot)

                it.cancel()

//                val cloned = ArrayDeque(placedShulkerBoxes)
//
//                cloned.forEachIndexed { index, openShulker ->
//                    if (index == 0) return@forEachIndexed
//                    placedShulkerBoxes.remove(openShulker)
//
//                    val currentBlock = world.getBlockState(openShulker.containerPos).block
//
//                    if (!(currentBlock is BlockShulkerBox || currentBlock is BlockEnderChest)) return@forEachIndexed
//
//                    ActivityManager.addSubActivities(
//                        BreakBlock(openShulker.containerPos, collectDrops = true)
//                    )
//                }

//                if (placedShulkerBoxes.size > 1) {
//                    val previous = placedShulkerBoxes.removeFirst()
//
//                    val currentBlock = world.getBlockState(previous.containerPos).block
//
//                    if (!(currentBlock is BlockShulkerBox || currentBlock is BlockEnderChest)) return@safeListener
//
//                    ActivityManager.addSubActivities(
//                        BreakBlock(previous.containerPos, collectDrops = true)
//                    )
//                }
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (placedShulkerBoxes.isEmpty() || it.phase != TickEvent.Phase.START) return@safeListener

            val cloned = ArrayDeque(placedShulkerBoxes)

            cloned.forEachIndexed { index, openShulker ->
                if (index == 0) return@forEachIndexed
                placedShulkerBoxes.remove(openShulker)

                val currentBlock = world.getBlockState(openShulker.containerPos).block

                if (!(currentBlock is BlockShulkerBox || currentBlock is BlockEnderChest)) return@forEachIndexed

                ActivityManager.addSubActivities(
                    BreakBlock(openShulker.containerPos, collectDrops = true)
                )
            }
        }

//        safeListener<GuiEvent.Closed> {
//            if (!(it.screen is GuiShulkerBox || it.screen is GuiChest)) return@safeListener
//
//            placedShulkerBoxes.firstOrNull()?.let { openShulkerFromSlot ->
//                placedShulkerBoxes.remove(openShulkerFromSlot)
//
//                val currentBlock = world.getBlockState(openShulkerFromSlot.containerPos).block
//
//                if (!(currentBlock is BlockShulkerBox || currentBlock is BlockEnderChest)) return@safeListener
//
//                ActivityManager.addSubActivities(
//                    BreakBlock(openShulkerFromSlot.containerPos, collectDrops = true)
//                )
//            }
//        }
    }
}