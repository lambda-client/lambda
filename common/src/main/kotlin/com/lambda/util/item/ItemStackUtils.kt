package com.lambda.util.item

import com.lambda.util.collections.Cacheable.Companion.cacheable
import net.minecraft.inventory.Inventories
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtElement
import net.minecraft.util.collection.DefaultedList

object ItemStackUtils {
    val ItemStack.spaceLeft get() = maxCount - count
    val ItemStack.hasSpace get() = spaceLeft > 0
    val List<ItemStack>.spaceLeft get() = sumOf { it.spaceLeft }
    val List<ItemStack>.empty: Int get() = count { it.isEmpty }
    val List<ItemStack>.count: Int get() = sumOf { it.count }
    val List<ItemStack>.copy: List<ItemStack> get() = map { it.copy() }

    val List<ItemStack>.compressed: List<ItemStack> get() =
        fold(mutableListOf()) { acc, itemStack ->
            acc merge itemStack
            acc
        }

    infix fun List<ItemStack>.merge(other: ItemStack): List<ItemStack> {
        return flatMap {
            it merge other
        }
    }

    infix fun ItemStack.merge(other: ItemStack): List<ItemStack> {
        if (!isStackable || !other.isStackable) {
            return listOf(this, other)
        }

        val newCount = count + other.count
        if (newCount <= maxCount) {
            return listOf(copyWithCount(newCount))
        }
        val remainder = newCount - maxCount
        return listOf(copyWithCount(maxCount), copyWithCount(remainder))
    }

    val ItemStack.shulkerBoxContents: List<ItemStack> by cacheable { stack ->
        BlockItem.getBlockEntityNbt(stack)?.takeIf {
            it.contains("Items", NbtElement.LIST_TYPE.toInt())
        }?.let {
            val list = DefaultedList.ofSize(27, ItemStack.EMPTY)
            Inventories.readNbt(it, list)
            list
        } ?: emptyList()
    }

    /**
     * Checks if the given item stacks are equal, including the item count and NBT.
     *
     * @param other The other item stack to compare with.
     * @return `true` if the item stacks are equal, `false` otherwise.
     * @see ItemStack.areItemsEqual Checks if the items in two item stacks are equal.
     * @see ItemStack.canCombine Checks if two item stacks can be combined into one stack.
     */
    fun ItemStack?.equal(other: ItemStack?) = ItemStack.areEqual(this, other)

    fun ItemStack.combines(other: ItemStack) = ItemStack.canCombine(this, other)
}