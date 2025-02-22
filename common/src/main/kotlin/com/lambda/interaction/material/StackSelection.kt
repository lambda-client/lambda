/*
 * Copyright 2024 Lambda
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

package com.lambda.interaction.material

import com.lambda.util.BlockUtils.item
import com.lambda.util.item.ItemStackUtils.shulkerBoxContents
import net.minecraft.block.Block
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot
import kotlin.reflect.KClass

/**
 * [StackSelection] is a class that holds a predicate for matching [ItemStack]s.
 */
class StackSelection {
    var selector: (ItemStack) -> Boolean = { true }
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

    val filterStack: (ItemStack) -> Boolean
        get() = { stack ->
            if (inShulkerBox) {
                stack.shulkerBoxContents.any { selector(it) }
            } else {
                selector(stack)
            }
        }

    val filterSlot: (Slot) -> Boolean
        get() = { slot ->
            filterStack(slot.stack)
        }

    val filterStacks: (List<ItemStack>) -> List<ItemStack>
        get() = {
            it.filter(filterStack)
        }

    val filterSlots: (List<Slot>) -> List<Slot>
        get() = { slots ->
            slots.filter { filterSlot(it) }
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

    /**
     * [isItem] returns a predicate that matches a specific [Item].
     * @param item The [Item] to be matched.
     * @return A predicate that matches the [Item].
     */
    @StackSelectionDsl
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
    @StackSelectionDsl
    fun isOneOfItems(items: Collection<Item>): (ItemStack) -> Boolean = { it.item in items }

    /**
     * Returns a predicate that checks if a given `ItemStack` exists within the provided collection of `ItemStack`s.
     *
     * @param stacks A collection of `ItemStack` instances to be checked against.
     * @return A predicate that evaluates to `true` if the given `ItemStack` is within the specified collection, otherwise `false`.
     */
    @StackSelectionDsl
    fun isOneOfStacks(stacks: Collection<ItemStack>): (ItemStack) -> Boolean = { it in stacks }

    /**
     * [isItem] returns a predicate that matches a specific [Item] instance.
     * @param T The instance of [Item] to be matched.
     * @return A predicate that matches the [Item].
     */
    @StackSelectionDsl
    inline fun <reified T : Item> isItem(): (ItemStack) -> Boolean {
        itemClass = T::class
        return { it.item is T }
    }

    /**
     * [isBlock] returns a predicate that matches a specific [Block].
     * @param block The [Block] to be matched.
     * @return A predicate that matches the [Block].
     */
    @StackSelectionDsl
    fun isBlock(block: Block): (ItemStack) -> Boolean {
        item = block.item
        return { it.item == block.item }
    }

    /**
     * [isItemStack] returns a predicate that matches a specific [ItemStack].
     * @param stack The [ItemStack] to be matched.
     * @return A predicate that matches the [ItemStack].
     */
    @StackSelectionDsl
    fun isItemStack(stack: ItemStack): (ItemStack) -> Boolean {
        this.itemStack = stack
        return { ItemStack.areEqual(it, stack) }
    }

    /**
     * [hasDamage] returns a predicate that matches a specific damage value.
     * @param damage The damage value to be matched.
     * @return A predicate that matches the damage value.
     */
    @StackSelectionDsl
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
    @StackSelectionDsl
    fun hasEnchantment(enchantment: Enchantment, level: Int = -1): (ItemStack) -> Boolean = {
        if (level < 0) {
            EnchantmentHelper.getLevel(enchantment, it) > 0
        } else {
            EnchantmentHelper.getLevel(enchantment, it) == level
        }
    }

    /**
     * Returns the negation of the original predicate.
     * @return A new predicate that matches if the original predicate does not match.
     */
    @StackSelectionDsl
    fun ((ItemStack) -> Boolean).not(): (ItemStack) -> Boolean {
        return { !this(it) }
    }

    /**
     * Combines two predicates using the logical AND operator.
     * @param otherPredicate The second predicate.
     * @return A new predicate that matches if both inputs predicate match.
     */
    @StackSelectionDsl
    infix fun ((ItemStack) -> Boolean).and(otherPredicate: (ItemStack) -> Boolean): (ItemStack) -> Boolean {
        return { this(it) && otherPredicate(it) }
    }

    /**
     * Combines two predicates using the logical OR operator.
     * @param otherPredicate The second predicate.
     * @return A new predicate that matches if either input predicate matches.
     */
    @StackSelectionDsl
    infix fun ((ItemStack) -> Boolean).or(otherPredicate: (ItemStack) -> Boolean): (ItemStack) -> Boolean {
        return { this(it) || otherPredicate(it) }
    }

    override fun toString() = buildString {
        append("selection of ${count}x ")
        item?.let { append(it.name.string) }
        itemClass?.let { append(it.simpleName) }
        itemStack?.let { append(it.name.string) }
        damage?.let { append(" with damage $it") }
    }

    companion object {
        @DslMarker
        annotation class StackSelectionDsl

        const val DEFAULT_AMOUNT = 1
        val FULL_SHULKERS: (ItemStack) -> Boolean = { stack ->
            stack.shulkerBoxContents.none { it.isEmpty }
        }
        val EMPTY_SHULKERS: (ItemStack) -> Boolean = { stack ->
            stack.shulkerBoxContents.all { it.isEmpty }
        }
        val EVERYTHING: (ItemStack) -> Boolean = { true }
        val NOTHING: (ItemStack) -> Boolean = { false }

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

        /**
         * Builds a [StackSelection] with the given parameters.
         * @param count The count of items to be selected.
         * @param block The predicate to be used to select the items.
         * @return A [StackSelection] with the given parameters.
         */
        @StackSelectionDsl
        fun selectStack(
            count: Int = DEFAULT_AMOUNT,
            inShulkerBox: Boolean = false,
            block: StackSelection.() -> (ItemStack) -> Boolean,
        ) = StackSelection().apply {
            selector = block()
            this.count = count
            this.inShulkerBox = inShulkerBox
        }
    }
}
