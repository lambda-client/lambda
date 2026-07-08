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

import com.lambda.util.EnchantmentUtils.getEnchantment
import com.lambda.util.item.ItemStackUtils.shulkerBoxStacks
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

@Suppress("unused")
class StackSelection(
	val count: Int = 0,
	val item: Item? = null,
	val itemStack: ItemStack? = null,
	val inShulkerBox: Boolean = false,
	val comparator: Comparator<StackAndSlot<*>> = compareBy { it.stack.count },
	val selector: (ItemStack, Slot?) -> Boolean,
) {
	val optimalStack = itemStack ?: item?.let { ItemStack(it, count) }

	@StackSelectionMarker
	fun bestMatch(stacks: List<ItemStack>) = filterStacks(stacks).firstOrNull()
	@StackSelectionMarker
	fun bestMatch(slots: List<Slot>) = filterSlots(slots).firstOrNull()

	@StackSelectionMarker
	fun matches(stack: ItemStack) = filterStack(stack)
	@StackSelectionMarker
	fun matches(slot: Slot) = filterSlot(slot)

	@StackSelectionMarker
	fun filterStack(stack: ItemStack) =
		if (inShulkerBox) stack.shulkerBoxStacks.any { selector(it, null) }
		else selector(stack, null)
	@StackSelectionMarker
	fun filterSlot(slot: Slot): Boolean =
		if (inShulkerBox) slot.stack.shulkerBoxStacks.any { selector(it, null) }
		else selector(slot.stack, slot)

	@StackSelectionMarker
	fun filterStacks(stacks: List<ItemStack>): List<ItemStack> =
		stacks
			.filter(::filterStack)
			.map { StackAndSlot<Slot?>(it, null) }
			.sortedWith(comparator)
			.map { it.stack }
	@StackSelectionMarker
	fun filterSlots(slots: List<Slot>): List<Slot> =
		slots
			.filter(::filterSlot)
			.map { StackAndSlot(it.stack, it) }
			.sortedWith(comparator)
			.map { it.slot }

	companion object {
		val EVERYTHING = StackSelection { _, _ -> true }
		val NOTHING = StackSelection { _, _ -> false }
	}
}

@DslMarker
private annotation class StackSelectionMarker

@Suppress("unused")
@StackSelectionMarker
class StackSelectionBuilder(private val count: Int = 0) {
	private var selector: (ItemStack, Slot?) -> Boolean = { _, _ -> true }
	private var item: Item? = null
	private var stackByRef = false
	private var itemStack: ItemStack? = null
	private var comparator: Comparator<StackAndSlot<*>> = compareBy { it.stack.count }
	private var inShulkerBoxesOnly: Boolean = false
	private var invertNewSelectors = false

	fun isItem(item: Item) {
		this.item = item
	}

	inline fun <reified T : Item> isItem() {
		val kClass = T::class
		appendSelector { stack, _ -> stack::class == kClass }
	}

	fun isItemStack(stack: ItemStack) {
		this.itemStack = stack
		appendSelector { s, _ -> ItemStack.areEqual(s, stack) }
	}

	fun isItemStackByRef(stack: ItemStack) {
		this.itemStack = stack
		appendSelector { s, _ -> s === stack }
	}

	fun inShulkerBoxesOnly() {
		inShulkerBoxesOnly = true
	}

	fun isOneOfItems(items: Collection<Item>) {
		appendSelector { stack, _ -> items.contains(stack.item) }
	}

	fun isNoneOfItems(items: Collection<Item>) {
		appendSelector { stack, _ -> !items.contains(stack.item) }
	}

	fun isOneOfStacks(stacks: Collection<ItemStack>) {
		appendSelector { stack, _ -> stacks.contains(stack) }
	}

	fun isNoneOfStacks(stacks: Collection<ItemStack>) {
		appendSelector { stack, _ -> !stacks.contains(stack) }
	}

	fun isEfficientForBreaking(blockState: BlockState) {
		appendSelector { itemStack, _ ->
			val hasEfficientTool = efficientToolCache.getOrPut(blockState) {
				ItemUtils.tools.any { it.getMiningSpeed(it.defaultStack, blockState) > 1f }
			}
			if (hasEfficientTool) itemStack.item.getMiningSpeed(itemStack, blockState) > 1f
			else true
		}
	}

	fun isSuitableForBreaking(blockState: BlockState) {
		appendSelector { itemStack, _ ->
			!blockState.isToolRequired || itemStack.isSuitableFor(blockState)
		}
	}

	fun hasTag(tag: TagKey<Item>) {
		appendSelector { stack, _ -> stack.isIn(tag) }
	}

	fun hasUseAction(action: UseAction) {
		appendSelector { stack, _ -> stack.useAction == action }
	}

	fun isTool() = hasComponent(DataComponentTypes.TOOL)

	fun isFood() = hasComponent(DataComponentTypes.FOOD)

	fun hasComponent(type: ComponentType<*>) {
		appendSelector { stack, _ ->
			stack.components.contains(type)
		}
	}

	fun isBlock(block: Block) {
		item = block.asItem()
		appendSelector { stack, _ -> stack.item == block.asItem() }
	}

	fun hasDamage(damage: Int) {
		appendSelector { stack, _ -> stack.damage == damage }
	}

	fun hasEnchantment(enchantment: RegistryKey<Enchantment>, level: Int = -1) {
		appendSelector { stack, _ ->
			if (level < 0) {
				stack.getEnchantment(enchantment) > 0
			} else {
				stack.getEnchantment(enchantment) == level
			}
		}
	}

	fun inverted(block: () -> Unit) {
		invertNewSelectors = true
		block()
		invertNewSelectors = false
	}

	fun custom(predicate: (ItemStack, Slot?) -> Boolean) {
		appendSelector { stack, slot -> predicate(stack, slot) }
	}

	fun sortedWith(comparator: Comparator<StackAndSlot<*>>) {
		this.comparator = comparator
	}

	fun sortedWith(comparatorSupplier: () -> Comparator<StackAndSlot<*>>) {
		this.comparator = comparatorSupplier()
	}

	@PublishedApi
	internal fun appendSelector(selector: (ItemStack, Slot?) -> Boolean) {
		val invert = invertNewSelectors
		this.selector = { stack, slot ->
			this.selector(stack, slot) && (selector(stack, slot) xor invert)
		}
	}

	private fun build() =
		StackSelection(
			count,
			item,
			itemStack,
			inShulkerBoxesOnly,
			comparator,
			selector
		)

	companion object {
		private val efficientToolCache: MutableMap<BlockState, Boolean> = Collections.synchronizedMap<BlockState, Boolean>(mutableMapOf())

		@StackSelectionMarker
		fun Item.select(count: Int = 0) = selectStack(count) { isItem(this@select) }

		@StackSelectionMarker
		fun ItemStack.select(count: Int = 0) = selectStack(count) { isItemStack(this@select) }

		fun selectStack(
			count: Int = 0,
			builder: StackSelectionBuilder.() -> Unit
		) = StackSelectionBuilder(count).apply(builder).build()
	}
}

data class StackAndSlot<T : Slot?>(val stack: ItemStack, val slot: T)