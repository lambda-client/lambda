package com.lambda.client.buildtools.task

import com.lambda.client.buildtools.BuildToolsManager.buildStructure
import com.lambda.client.buildtools.BuildToolsManager.disableError
import com.lambda.client.buildtools.blueprint.StructureTask
import com.lambda.client.buildtools.task.TaskFactory.isInsideBlueprintBuilding
import com.lambda.client.buildtools.task.TaskProcessor.addTask
import com.lambda.client.buildtools.task.build.PlaceTask
import com.lambda.client.commons.extension.ceilToInt
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.AbstractModule
import com.lambda.client.module.modules.client.BuildTools.maxReach
import com.lambda.client.module.modules.client.BuildTools.preferEnderChests
import com.lambda.client.module.modules.client.BuildTools.storageManagement
import com.lambda.client.util.items.block
import com.lambda.client.util.items.inventorySlots
import com.lambda.client.util.math.VectorUtils
import com.lambda.client.util.math.VectorUtils.toVec3dCenter
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.world.isPlaceable
import com.lambda.client.util.world.isReplaceable
import net.minecraft.init.Blocks
import net.minecraft.inventory.ItemStackHelper
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemShulkerBox
import net.minecraft.item.ItemStack
import net.minecraft.util.NonNullList
import net.minecraft.util.math.BlockPos
import kotlin.math.abs

object RestockHandler {
    fun AbstractModule.createRestockStructure(item: Item) {
        runSafe {
            buildStructure(StructureTask(hashMapOf()))
            restockItem(item)
        }
    }

    fun SafeClientEvent.restockItem(item: Item) {
        if (!storageManagement) {
            disableError("Storage management is disabled. Can't restock ${item.registryName.toString()}")
            return
        }

        if (preferEnderChests && item.block == Blocks.OBSIDIAN) {
            grindObsidian()
            return
        }

        getShulkerWith(player.inventorySlots, item)?.let { slot ->
            getBestContainerPosition()?.let {
                val containerTask = PlaceTask(it, slot.stack.item.block, isContainerTask = true)
                containerTask.desiredItem = item
                containerTask.slotToUseForPlace = slot

                addTask(containerTask)
            } ?: run {
                disableError("Can't find possible container position (Case: 1)")
            }
        } ?: run {
            restockFromEnderChest(item)
        }
    }

    fun grindObsidian() {

    }

    private fun SafeClientEvent.restockFromEnderChest(item: Item) {

    }

    private fun SafeClientEvent.getBestContainerPosition(): BlockPos? {
        val playerPos = player.positionVector
        return VectorUtils.getBlockPosInSphere(playerPos, maxReach).asSequence()
            .filter { pos ->
                !isInsideBlueprintBuilding(pos)
                    && world.isPlaceable(pos)
                    && !world.getBlockState(pos.down()).isReplaceable
                    && world.isAirBlock(pos.up())
            }.sortedWith(
                compareByDescending<BlockPos> {
                    secureScore(it)
                }.thenBy {
                    it.toVec3dCenter().distanceTo(playerPos).ceilToInt()
                }.thenBy {
                    abs(it.y - playerPos.y)
                }
            ).firstOrNull()
    }

    private fun SafeClientEvent.secureScore(pos: BlockPos): Int {
        var safe = 0
        if (!world.getBlockState(pos.down()).isReplaceable) safe += 5
        if (!world.getBlockState(pos.down().north()).isReplaceable) safe++
        if (!world.getBlockState(pos.down().east()).isReplaceable) safe++
        if (!world.getBlockState(pos.down().south()).isReplaceable) safe++
        if (!world.getBlockState(pos.down().west()).isReplaceable) safe++
        return safe
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