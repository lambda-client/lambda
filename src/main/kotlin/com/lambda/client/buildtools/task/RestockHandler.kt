package com.lambda.client.buildtools.task

import com.lambda.client.buildtools.BuildToolsManager.disableError
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.client.BuildTools.preferEnderChests
import com.lambda.client.module.modules.client.BuildTools.storageManagement
import com.lambda.client.util.items.block
import com.lambda.client.util.items.inventorySlots
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.inventory.ItemStackHelper
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemShulkerBox
import net.minecraft.item.ItemStack
import net.minecraft.util.NonNullList

object RestockHandler {
    inline fun <reified T : IBlockState> handleRestock() {

    }

    inline fun <reified T : Item> SafeClientEvent.handleRestock() {
        handleRestock(T::class.java.newInstance())
    }

    fun SafeClientEvent.handleRestock(item: Item) {
        if (!storageManagement) {
            disableError("Storage management is disabled. Can't restock ${item.registryName}")
            return
        }

        if (preferEnderChests && item.block == Blocks.OBSIDIAN) {
            grindObsidian()
            return
        }

        getShulkerWith(player.inventorySlots, item)
    }

    fun grindObsidian() {

    }

    fun getShulkerWith(slots: List<Slot>, item: Item) =
        slots.filter {
            it.stack.item is ItemShulkerBox && getShulkerData(it.stack, item) > 0
        }.minByOrNull {
            getShulkerData(it.stack, item)
        }

    private fun getShulkerData(stack: ItemStack, item: Item): Int {
        if (stack.item !is ItemShulkerBox) return 0

        stack.tagCompound?.let { tagCompound ->
            if (tagCompound.hasKey("BlockEntityTag", 10)) {
                val blockEntityTag = tagCompound.getCompoundTag("BlockEntityTag")

                if (blockEntityTag.hasKey("Items", 9)) {
                    val shulkerInventory = NonNullList.withSize(27, ItemStack.EMPTY)
                    ItemStackHelper.loadAllItems(blockEntityTag, shulkerInventory)
                    return shulkerInventory.count { it.item == item }
                }
            }
        }

        return 0
    }
}