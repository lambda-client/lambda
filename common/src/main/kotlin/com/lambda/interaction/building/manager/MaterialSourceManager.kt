package com.lambda.interaction.building.manager

import com.lambda.context.SafeContext
import com.lambda.interaction.building.material.SourceSelection
import com.lambda.interaction.building.material.SourceSelection.Companion.selectSource
import com.lambda.interaction.building.material.source.MaterialSource
import com.lambda.interaction.building.material.source.Source
import com.lambda.module.modules.client.TaskFlow.disposables
import com.lambda.interaction.building.material.StackSelection
import com.lambda.interaction.building.material.StackSelection.Companion.select
import com.lambda.util.ItemUtils
import com.lambda.util.player.SlotUtils.combined
import net.minecraft.block.BlockState
import net.minecraft.inventory.Inventories
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtElement
import net.minecraft.util.collection.DefaultedList

/**
 * Caches all the item sources and destinations and gives a simple interface to check if an item is available and where.
 * Sources are:
 * 1. Player inventory
 * 2. Shulker boxes
 * 3. Ender chest
 * 4. Stash
 */
object MaterialSourceManager {

    val cache: MutableSet<MaterialSource> = mutableSetOf()

    val SafeContext.enderChestContent: List<ItemStack>
        get() = emptyList()

    val SafeContext.stashContent: List<ItemStack>
        get() = emptyList()

    val SafeContext.playerSources: Set<MaterialSource>
        get() {
            if (player.isCreative) {
                return Source.CREATIVE.gather(player)
            }

            return Source.INVENTORY.gather(player)
        }

    val SafeContext.enderChestSources: Set<MaterialSource>
        get() = Source.ENDER_CHEST.gather(player)

    val SafeContext.stashSources: Set<MaterialSource>
        get() = Source.STASH.gather(player)

    val SafeContext.sources: Set<MaterialSource>
        get() = playerSources + enderChestSources + stashSources

    fun SafeContext.getAvailableCount(stackSelection: StackSelection): Int {
        return sources.sumOf { it.available(stackSelection) }
    }

    fun SafeContext.getSourcesWith(stackSelection: StackSelection): List<MaterialSource> {
        return sources.filter { it.available(stackSelection) >= stackSelection.count }
    }

    fun SafeContext.getAvailableStacks(selection: SourceSelection): List<ItemStack> {
        return sources.filter(selection.selection).flatMap { it.stacks }
    }

    fun SafeContext.getGroupedAvailableStacks(): Map<MaterialSource, List<ItemStack>> {
        return sources.associateWith { it.stacks }
    }

    fun getShulkerBoxContents(itemStack: ItemStack) =
        BlockItem.getBlockEntityNbt(itemStack)?.takeIf {
            it.contains("Items", NbtElement.LIST_TYPE.toInt())
        }?.let {
            val list = DefaultedList.ofSize(27, ItemStack.EMPTY)
            Inventories.readNbt(it, list)
            list
        } ?: emptyList()

    fun SafeContext.getBestAvailableTool(
        blockState: BlockState,
        availableTools: Set<Item> = ItemUtils.tools,
    ) =
        availableTools.map {
            it to it.getMiningSpeedMultiplier(it.defaultStack, blockState)
        }.filter { (item, speed) ->
            speed > 1.0 &&
                item.isSuitableFor(blockState) &&
                selectSource {
                    hasItem(item)
                }?.let {
                    it.available(item.select()) > 0
                } == true
        }.maxByOrNull {
            it.second
        }?.first

    fun SafeContext.nextDisposable() = player.combined.firstOrNull { it.item in disposables }

//    fun SafeContext.nextDisposable() = TaskFlow.disposables.firstOrNull {
//        val selection = selectStack { isItem(it) }
//        (selectSource { matching(selection) }?.available(selection) ?: 0) > 0
//    }
}
