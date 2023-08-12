package com.lambda.client.activity.activities.storage.types

import com.lambda.client.activity.getShulkerInventory
import com.lambda.client.util.items.block
import com.lambda.client.util.items.item
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.init.Blocks
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

/**
 * [StackSelection] is a class that holds a predicate for matching [ItemStack]s.
 * @param count The count of item stacks to be matched.
 * @param selection The predicate for matching [ItemStack]s.
 */
class StackSelection(
    var count: Int = DEFAULT_AMOUNT,
    val inShulkerBox: Boolean = false
) {
    lateinit var selection: (ItemStack) -> Boolean

    var item: Item? = null
    private var metadata: Int? = null
    private var damage: Int? = null
    private var itemStack: ItemStack? = null

    val filter: (Slot) -> Boolean get() = { slot ->
        if (inShulkerBox) {
            getShulkerInventory(slot.stack)?.any { selection(it) } ?: false
        } else selection(slot.stack)
    }

    val optimalStack: ItemStack?
        get() = item?.let { ItemStack(it, count, metadata ?: 0) }

    val placementResult: IBlockState
        get() = itemStack?.item?.block?.defaultState
            ?: optimalStack?.item?.block?.defaultState
            ?: Blocks.AIR.defaultState

    /**
     * returns a function that finds a shulker box to push matching items into.
     */
    val findShulkerToPush = { slot: Slot ->
        getShulkerInventory(slot.stack)?.let { inventory ->
            if (inventory.all { selection(it) || it.isEmpty }) {
                val storableItems = inventory.sumOf {
//                    if (it.isEmpty) item.itemStackLimit else it.maxStackSize - it.count
                    if (it.isEmpty) item?.itemStackLimit ?: 0 else 0
                }

                if (storableItems > 0) slot to storableItems else null
            } else null
        }
    }

    /**
     * returns a function that finds a shulker box to pull matching items from.
     */
    val findShulkerToPull = { slot: Slot ->
        getShulkerInventory(slot.stack)?.let { inventory ->
            val usableItems = inventory.sumOf { if (selection(it)) it.count else 0 }

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
    inline fun <reified T: Item> isItem(): (ItemStack) -> Boolean = { it.item is T }

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
        return { ItemStack.areItemStacksEqual(it, stack) }
    }

    /**
     * [hasMetadata] returns a predicate that matches a specific `metadata`.
     * @param metadata The `metadata` to be matched.
     * @return A predicate that matches the `metadata`.
     */
    fun hasMetadata(metadata: Int): (ItemStack) -> Boolean {
        this.metadata = metadata
        return { it.metadata == metadata }
    }

    /**
     * [hasDamage] returns a predicate that matches a specific damage value.
     * @param damage The damage value to be matched.
     * @return A predicate that matches the damage value.
     */
    fun hasDamage(damage: Int): (ItemStack) -> Boolean {
        this.damage = damage
        return { it.itemDamage == damage }
    }

    /**
     * [hasEnchantment] returns a predicate that matches a specific [Enchantment] and level.
     * @param enchantment The [Enchantment] to be matched.
     * @param level The level to be matched (if -1 will look for any level above 0).
     * @return A predicate that matches the [Enchantment] and `level`.
     */
    fun hasEnchantment(enchantment: Enchantment, level: Int = -1): (ItemStack) -> Boolean = {
        if (level < 0) EnchantmentHelper.getEnchantmentLevel(enchantment, it) > 0
        else EnchantmentHelper.getEnchantmentLevel(enchantment, it) == level
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
     * @return A new predicate that matches if both input predicates match.
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

    companion object {
        const val DEFAULT_AMOUNT = 1
        val FULL_SHULKERS: (ItemStack) -> Boolean = { stack ->
            getShulkerInventory(stack)?.none { it.isEmpty } == true
        }
        val EMPTY_SHULKERS: (ItemStack) -> Boolean = { stack ->
            getShulkerInventory(stack)?.all { it.isEmpty } == true
        }
        val EVERYTHING: (ItemStack) -> Boolean = { true }
    }
}