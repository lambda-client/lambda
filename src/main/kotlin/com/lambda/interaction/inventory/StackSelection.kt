/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.interaction.inventory

import com.lambda.interaction.inventory.StackSelection.Companion.StackSelectionDsl
import com.lambda.util.EnchantmentUtils.getEnchantment
import com.lambda.util.item.ItemStackUtils.shulkerBoxContents
import com.lambda.util.item.ItemUtils
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.component.ComponentType
import net.minecraft.component.DataComponentTypes
import net.minecraft.enchantment.Enchantment
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.consume.UseAction
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.tag.TagKey
import net.minecraft.screen.slot.Slot
import java.util.*
import kotlin.reflect.KClass

/**
 * [StackSelection] is a class that holds a predicate for matching [ItemStack]s.
 */
@Suppress("unused")
@StackSelectionDsl
class StackSelection {
    var selector: (ItemStack) -> Boolean = EVERYTHING
    var comparator: Comparator<ItemStack> = NO_COMPARE
    var count: Int = DEFAULT_AMOUNT
    var inShulkerBox: Boolean = false

    var item: Item? = null
    var itemClass: KClass<out Item>? = null
    private var damage: Int? = null
    var itemStack: ItemStack? = null
    val stackSize: Int
        get() = optimalStack?.maxCount ?: 64

    val optimalStack: ItemStack?
        get() = itemStack ?: item?.let { ItemStack(it, count) }

    /**
     * Filters the given [stacks], sorts them with the [comparator] and returns the first value
     */
    fun bestItemMatch(stacks: List<ItemStack>): ItemStack? = filterStacks(stacks).firstOrNull()

    fun matches(stack: ItemStack): Boolean = filterStack(stack)

    fun filterStack(stack: ItemStack) =
        if (inShulkerBox) stack.shulkerBoxContents.any { selector(it) }
        else selector(stack)

    fun filterSlot(slot: Slot): Boolean = filterStack(slot.stack)

    fun filterStacks(stacks: List<ItemStack>): List<ItemStack> =
        stacks.filter(::filterStack).sortedWith(comparator)

    fun filterSlots(slots: List<Slot>): List<Slot> =
        slots.filter(::filterSlot).sortedWith { slot, slot2 -> comparator.compare(slot.stack, slot2.stack) }

    fun <R : Comparable<R>> sortBy(selector: (ItemStack) -> R?): StackSelection = apply {
        comparator = compareBy(selector)
    }

    fun <R : Comparable<R>> sortByDescending(selector: (ItemStack) -> R?): StackSelection = apply {
        comparator = compareByDescending(selector)
    }

    fun <R : Comparable<R>> thenBy(selector: (ItemStack) -> R?): StackSelection = apply {
        check(comparator != NO_COMPARE) { "No comparator specified" }
        comparator = comparator.thenBy(selector)
    }

    fun <R : Comparable<R>> thenByDescending(selector: (ItemStack) -> R?): StackSelection = apply {
        check(comparator != NO_COMPARE) { "No comparator specified" }
        comparator = comparator.thenByDescending(selector)
    }

    fun sortWith(custom: Comparator<ItemStack>): StackSelection = apply {
        comparator = custom
    }

    fun reversed(): StackSelection = apply {
        comparator = comparator.reversed()
    }

    /**
     * returns a function that finds a shulker box to push matching items into.
     */
    val findShulkerToPush = { stack: ItemStack ->
        stack.shulkerBoxContents.let { inventory ->
            if (inventory.all { selector(it) || it.isEmpty }) {
                val storableItems = inventory.sumOf {
//                    if (it.isEmpty) item.itemStackLimit else it.maxStackSize - it.count
                    if (it.isEmpty) item?.maxCount ?: 0 else 0
                }

                if (storableItems > 0) storableItems else null
            } else {
                null
            }
        }
    }

    /**
     * returns a function that finds a shulker box to pull matching items from.
     */
    val findShulkerToPull = { slot: Slot ->
        slot.stack.shulkerBoxContents.let { inventory ->
            val usableItems = inventory.sumOf { if (selector(it)) it.count else 0 }

            if (usableItems > 0) slot to usableItems else null
        }
    }

    fun any() = EVERYTHING
    fun none() = NOTHING

    /**
     * [isItem] returns a predicate that matches a specific [Item].
     * @param item The [Item] to be matched.
     * @return A predicate that matches the [Item].
     */
    fun isItem(item: Item): (ItemStack) -> Boolean {
        this.item = item
        return { it.item == item }
    }

    /**
     * Returns a predicate that matches if the `ItemStack`'s item is one of the specified items in the collection.
     *
     * @param items The collection of `Item` instances to match against.
     * @return A predicate that checks if the `ItemStack`'s item is contained in the provided collection.
     */
    fun isOneOfItems(items: Collection<Item>): (ItemStack) -> Boolean = { it.item in items }

    fun isNoneOfItems(items: Collection<Item>): (ItemStack) -> Boolean = isOneOfItems(items).not()

    /**
     * Returns a predicate that checks if a given `ItemStack` exists within the provided collection of `ItemStack`s.
     *
     * @param stacks A collection of `ItemStack` instances to be checked against.
     * @return A predicate that evaluates to `true` if the given `ItemStack` is within the specified collection, otherwise `false`.
     */
    fun isOneOfStacks(stacks: Collection<ItemStack>): (ItemStack) -> Boolean = stacks::contains

    fun isEfficientForBreaking(blockState: BlockState): (ItemStack) -> Boolean = { itemStack ->
        val hasEfficientTool = efficientToolCache.getOrPut(blockState) {
            ItemUtils.tools.any { it.getMiningSpeed(it.defaultStack, blockState) > 1f }
        }
        if (hasEfficientTool) itemStack.item.getMiningSpeed(itemStack, blockState) > 1f
        else true
    }

    fun isSuitableForBreaking(blockState: BlockState): (ItemStack) -> Boolean = { !blockState.isToolRequired || it.isSuitableFor(blockState) }

    fun hasTag(tag: TagKey<Item>): (ItemStack) -> Boolean = { it.isIn(tag) }

    fun hasUseAction(action: UseAction): (ItemStack) -> Boolean = { it.useAction == action }

    fun isTool(): (ItemStack) -> Boolean = hasComponent(DataComponentTypes.TOOL)

    fun isFood(): (ItemStack) -> Boolean = hasComponent(DataComponentTypes.FOOD)

    fun hasComponent(type: ComponentType<*>): (ItemStack) -> Boolean = { it.components.contains(type) }

    /**
     * [isItem] returns a predicate that matches a specific [Item] instance.
     * @param T The instance of [Item] to be matched.
     * @return A predicate that matches the [Item].
     */
    inline fun <reified T : Item> isItem(): (ItemStack) -> Boolean {
        itemClass = T::class
        return { it.item is T }
    }

    /**
     * [isBlock] returns a predicate that matches a specific [Block].
     * @param block The [Block] to be matched.
     * @return A predicate that matches the [Block].
     */
    fun isBlock(block: Block): (ItemStack) -> Boolean {
        item = block.asItem()
        return { it.item == block.asItem() }
    }

    /**
     * [isItemStack] returns a predicate that matches a specific [ItemStack].
     * @param stack The [ItemStack] to be matched.
     * @return A predicate that matches the [ItemStack].
     */
    fun isItemStack(stack: ItemStack): (ItemStack) -> Boolean {
        this.itemStack = stack
        return { ItemStack.areEqual(it, stack) }
    }

    /**
     * [hasDamage] returns a predicate that matches a specific damage value.
     * @param damage The damage value to be matched.
     * @return A predicate that matches the damage value.
     */
    fun hasDamage(damage: Int): (ItemStack) -> Boolean {
        this.damage = damage
        return { it.damage == damage }
    }

    /**
     * [hasEnchantment] returns a predicate that matches a specific [Enchantment] and level.
     * @param enchantment The [Enchantment] to be matched.
     * @param level The level to be matched (if -1 will look for any level above 0).
     * @return A predicate that matches the [Enchantment] and `level`.
     */
    fun hasEnchantment(enchantment: RegistryKey<Enchantment>, level: Int = -1): (ItemStack) -> Boolean = {
        if (level < 0) {
            it.getEnchantment(enchantment) > 0
        } else {
            it.getEnchantment(enchantment) == level
        }
    }

    /**
     * Returns the negation of the original predicate.
     * @return A new predicate that matches if the original predicate does not match.
     */
    fun ((ItemStack) -> Boolean).not(): (ItemStack) -> Boolean = { !this(it) }

    /**
     * Combines two predicates using the logical AND operator.
     * @param otherPredicate The second predicate.
     * @return A new predicate that matches if both inputs predicate match.
     */
    infix fun ((ItemStack) -> Boolean).and(otherPredicate: (ItemStack) -> Boolean): (ItemStack) -> Boolean = { this(it) && otherPredicate(it) }

    /**
     * Combines two predicates using the logical OR operator.
     * @param otherPredicate The second predicate.
     * @return A new predicate that matches if either input predicate matches.
     */
    infix fun ((ItemStack) -> Boolean).or(otherPredicate: (ItemStack) -> Boolean): (ItemStack) -> Boolean = { this(it) || otherPredicate(it) }

    fun ((ItemStack) -> Boolean).andIf(predicate: Boolean, otherPredicate: () -> (ItemStack) -> Boolean): (ItemStack) -> Boolean =
        if (predicate) { { this(it) && otherPredicate()(it) } } else this

    override fun toString() = buildString {
        append("selection of ${count}x ")
        item?.let { append(it.name.string) }
        itemClass?.let { append(it.simpleName) }
        itemStack?.let { append(it.name.string) }
        damage?.let { append(" with damage $it") }
        when (selector) {
            EVERYTHING -> append(" everything")
            NOTHING -> append(" nothing")
            else -> append(" custom predicate")
        }
        if (inShulkerBox) append(" in shulker box")
        if (comparator != NO_COMPARE) append(" sorted by custom comparator")
    }

    companion object {
        @DslMarker
        annotation class StackSelectionDsl

        const val DEFAULT_AMOUNT = 1

        val FULL_SHULKERS: (ItemStack) -> Boolean = { stack -> stack.shulkerBoxContents.none { it.isEmpty } }
        val EMPTY_SHULKERS: (ItemStack) -> Boolean = { stack -> stack.shulkerBoxContents.all { it.isEmpty } }
        val EVERYTHING: (ItemStack) -> Boolean = { true }
        val NOTHING: (ItemStack) -> Boolean = { false }
        val NO_COMPARE: Comparator<ItemStack> = Comparator { _, _ -> 0 }

        val efficientToolCache: MutableMap<BlockState, Boolean> = Collections.synchronizedMap<BlockState, Boolean>(mutableMapOf())

        @StackSelectionDsl
        fun Item.select() = selectStack { isItem(this@select) }

        @StackSelectionDsl
        fun ItemStack.select() = selectStack { isItemStack(this@select) }

        @StackSelectionDsl
        @JvmName("selectStacks")
        fun Collection<ItemStack>.select() = selectStack { isOneOfStacks(this@select) }

        @StackSelectionDsl
        @JvmName("selectItems")
        fun Collection<Item>.select() = selectStack { isOneOfItems(this@select) }

        @StackSelectionDsl
        fun ((ItemStack) -> Boolean).select() = selectStack { this@select }

        @StackSelectionDsl
        fun selectStack(
            count: Int = DEFAULT_AMOUNT,
            inShulkerBox: Boolean = false,
            sorter: Comparator<ItemStack> = NO_COMPARE,
            block: StackSelection.() -> (ItemStack) -> Boolean = { EVERYTHING },
        ) = StackSelection().apply {
            selector = block()
            comparator = sorter
            this.count = count
            this.inShulkerBox = inShulkerBox
        }
    }
}
