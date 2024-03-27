package com.lambda.interaction.building.material

import com.lambda.interaction.building.manager.MaterialSourceManager.getShulkerBoxContents
import com.lambda.util.BlockUtils.item
import net.minecraft.block.Block
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot

/**
 * [StackSelection] is a class that holds a predicate for matching [ItemStack]s.
 */
class StackSelection {
    var selector: (ItemStack) -> Boolean = { true }
    var count: Int = DEFAULT_AMOUNT
    var inShulkerBox: Boolean = false

    var item: Item? = null
    var itemClass: Class<out Item>? = null
    private var damage: Int? = null
    var itemStack: ItemStack? = null

    val filter: (Slot) -> Boolean
        get() = { slot ->
            if (inShulkerBox) {
                getShulkerBoxContents(slot.stack).any { selector(it) }
            } else {
                selector(slot.stack)
            }
        }

    val optimalStack: ItemStack?
        get() = itemStack ?: item?.let { ItemStack(it, count) }

    /**
     * returns a function that finds a shulker box to push matching items into.
     */
    val findShulkerToPush = { stack: ItemStack ->
        getShulkerBoxContents(stack).let { inventory ->
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
        getShulkerBoxContents(slot.stack).let { inventory ->
            val usableItems = inventory.sumOf { if (selector(it)) it.count else 0 }

            if (usableItems > 0) slot to usableItems else null
        }
    }

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
     * [isItem] returns a predicate that matches a specific [Item] instance.
     * @param T The instance of [Item] to be matched.
     * @return A predicate that matches the [Item].
     */
    inline fun <reified T : Item> isItem(): (ItemStack) -> Boolean {
        itemClass = T::class.java
        return { it.item is T }
    }

    /**
     * [isBlock] returns a predicate that matches a specific [Block].
     * @param block The [Block] to be matched.
     * @return A predicate that matches the [Block].
     */
    fun isBlock(block: Block): (ItemStack) -> Boolean {
        item = block.item
        return { it.item == block.item }
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
    fun ((ItemStack) -> Boolean).not(): (ItemStack) -> Boolean {
        return { !this(it) }
    }

    /**
     * Combines two predicates using the logical AND operator.
     * @param otherPredicate The second predicate.
     * @return A new predicate that matches if both inputs predicate match.
     */
    infix fun ((ItemStack) -> Boolean).and(otherPredicate: (ItemStack) -> Boolean): (ItemStack) -> Boolean {
        return { this(it) && otherPredicate(it) }
    }

    /**
     * Combines two predicates using the logical OR operator.
     * @param otherPredicate The second predicate.
     * @return A new predicate that matches if either input predicate matches.
     */
    infix fun ((ItemStack) -> Boolean).or(otherPredicate: (ItemStack) -> Boolean): (ItemStack) -> Boolean {
        return { this(it) || otherPredicate(it) }
    }

    override fun toString() = StringBuilder().apply {
        append("selection of ${count}x ")
        item?.let { append(it.name.string) }
        itemClass?.let { append(it.simpleName) }
        itemStack?.let { append(it.name.string) }
        damage?.let { append(" with damage $it") }
    }.toString()

    companion object {
        const val DEFAULT_AMOUNT = 1
        val FULL_SHULKERS: (ItemStack) -> Boolean = { stack ->
            getShulkerBoxContents(stack).none { it.isEmpty }
        }
        val EMPTY_SHULKERS: (ItemStack) -> Boolean = { stack ->
            getShulkerBoxContents(stack).all { it.isEmpty }
        }
        val EVERYTHING: (ItemStack) -> Boolean = { true }

        fun Item.select(): StackSelection = selectStack { isItem(this@select) }
        fun ItemStack.select(): StackSelection = selectStack { isItemStack(this@select) }

        /**
         * Builds a [StackSelection] with the given parameters.
         * @param count The count of items to be selected.
         * @param block The predicate to be used to select the items.
         * @return A [StackSelection] with the given parameters.
         */
        fun selectStack(
            count: Int = DEFAULT_AMOUNT,
            inShulkerBox: Boolean = false,
            block: StackSelection.() -> (ItemStack) -> Boolean,
        ): StackSelection {
            val stackSelection = StackSelection()
            stackSelection.selector = stackSelection.block()
            stackSelection.count = count
            stackSelection.inShulkerBox = inShulkerBox
            return stackSelection
        }
    }
}
