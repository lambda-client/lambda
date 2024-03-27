package com.lambda.interaction.building.material

import com.lambda.context.SafeContext
import com.lambda.interaction.building.manager.MaterialSourceManager.playerSources
import com.lambda.interaction.building.manager.MaterialSourceManager.sources
import com.lambda.interaction.building.material.source.MaterialSource
import com.lambda.interaction.building.material.source.Source
import com.lambda.interaction.building.material.source.sources.InventorySource
import com.lambda.interaction.building.material.source.sources.ShulkerBoxSource
import com.lambda.interaction.building.material.StackSelection.Companion.select
import net.minecraft.enchantment.Enchantment
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class SourceSelection {
    var selection: (MaterialSource) -> Boolean = { true }

    fun isSource(source: Source): (MaterialSource) -> Boolean {
        return { it.source == source }
    }

    fun hasItem(item: Item, count: Int = 1): (MaterialSource) -> Boolean {
        return { source ->
            source.available(item.select()) >= count
        }
    }

    fun hasEnchantment(enchantment: Enchantment, count: Int = 1): (MaterialSource) -> Boolean {
        return { source ->
            val a = StackSelection().hasEnchantment(enchantment, count)
            source.stacks.any { a(it) }
        }
    }

    fun hasStack(stack: ItemStack, count: Int = 1): (MaterialSource) -> Boolean {
        return { source ->
            source.available(stack.select()) >= count
        }
    }

    fun matching(stackSelection: StackSelection, count: Int = 1): (MaterialSource) -> Boolean {
        return { source ->
            source.available(stackSelection) >= count
        }
    }

    infix fun ((MaterialSource) -> Boolean).and(other: (MaterialSource) -> Boolean): (MaterialSource) -> Boolean {
        return { this(it) && other(it) }
    }

    infix fun ((MaterialSource) -> Boolean).or(other: (MaterialSource) -> Boolean): (MaterialSource) -> Boolean {
        return { this(it) || other(it) }
    }

    fun ((MaterialSource) -> Boolean).not(): (MaterialSource) -> Boolean {
        return { !this(it) }
    }

    companion object {
        fun SafeContext.selectSource(block: SourceSelection.() -> (MaterialSource) -> Boolean): MaterialSource? {
            return sources.filter(SourceSelection().block()).minOrNull()
        }

        fun SafeContext.selectSourceStacks(
            count: Int = StackSelection.DEFAULT_AMOUNT,
            selection: StackSelection.() -> (ItemStack) -> Boolean,
        ): MaterialSource? {
            val stackSelection = StackSelection()
            stackSelection.selector = stackSelection.selection()
            stackSelection.count = count
            return selectSource { matching(stackSelection) }
        }

        fun SafeContext.selectPlayerSource(block: SourceSelection.() -> (MaterialSource) -> Boolean): MaterialSource? {
            return playerSources.filter(SourceSelection().block()).minOrNull()
        }

        fun SafeContext.selectInventorySource(block: SourceSelection.() -> (MaterialSource) -> Boolean): InventorySource? {
            return playerSources
                .filterIsInstance<InventorySource>()
                .filter(SourceSelection().block()).minOrNull()
        }

        fun SafeContext.selectShulkerSource(block: SourceSelection.() -> (MaterialSource) -> Boolean): ShulkerBoxSource? {
            return sources
                .filterIsInstance<ShulkerBoxSource>()
                .filter(SourceSelection().block()).minOrNull()
        }
    }
}
