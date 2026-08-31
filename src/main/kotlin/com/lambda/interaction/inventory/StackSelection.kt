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

import com.lambda.interaction.inventory.StackSelectionBuilder.Companion.selectStack
import com.lambda.util.EnchantmentUtils.getEnchantment
import com.lambda.util.item.ItemStackUtils.shulkerBoxStacks
import com.lambda.util.item.ItemUtils
import com.lambda.util.player.SlotUtils.matches
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

@ContainerMarker
fun Item.select(count: Int = 0) = selectStack(count) { isItem(this@select) }

@ContainerMarker
fun ItemStack.select(count: Int = 0) = selectStack(count) { isItemStack(this@select) }

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

	@ContainerMarker
	fun bestMatch(stacks: Iterable<ItemStack>) = filter(stacks).firstOrNull()
	@ContainerMarker
	fun bestMatch(slots: Iterable<Slot>) = filter(slots).firstOrNull()

	@ContainerMarker
	fun matches(stack: ItemStack) =
		if (inShulkerBox) stack.shulkerBoxStacks.any { selector(it, null) }
		else selector(stack, null)
	@ContainerMarker
	fun matches(slot: Slot): Boolean =
		if (inShulkerBox) slot.stack.shulkerBoxStacks.any { selector(it, null) }
		else selector(slot.stack, slot)

	@ContainerMarker
	@JvmName("filter1")
	fun filter(stacks: Iterable<ItemStack>) =
		stacks
			.filter(::matches)
			.map { StackAndSlot<Slot?>(it, null) }
			.sortedWith(comparator)
			.map { it.stack }
	@ContainerMarker
	@JvmName("filter2")
	fun filter(slots: Iterable<Slot>) =
		slots
			.filter(::matches)
			.map { StackAndSlot(it.stack, it) }
			.sortedWith(comparator)
			.map { it.slot }

	companion object {
		val EVERYTHING = StackSelection { _, _ -> true }
		val NOTHING = StackSelection { _, _ -> false }
	}
}

@Suppress("unused")
@ContainerMarker
class StackSelectionBuilder private constructor(private val count: Int = 0) {
	private var selector: (ItemStack, Slot?) -> Boolean = { _, _ -> true }
	private var item: Item? = null
	private var stackByRef = false
	private var itemStack: ItemStack? = null
	private var comparator: Comparator<StackAndSlot<*>>? = null
	private var inShulkerBoxesOnly: Boolean = false
	private var invertNewSelectors = false

	fun isItem(item: Item) {
		this.item = item
		appendSelector { stack, _ -> stack.item == item }
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

	fun isShulkerBox() {
		appendSelector { stack, _ -> stack.item in ItemUtils.shulkerBoxes }
	}

	fun hasCustomName(name: String) {
		appendSelector { stack, _ ->
			stack.name.string == name
		}
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

	fun isEmpty() {
		appendSelector { stack, _ -> stack.isEmpty}
	}

	fun isSlot(slot: Slot) {
		appendSelector { _, s ->
			s != null && s matches slot
		}
	}

	fun isSlotByReference(slot: Slot) {
		appendSelector { _, s -> s === slot }
	}

	fun inIndex(index: Int) {
		appendSelector { _, s -> s?.index == index }
	}

	fun custom(predicate: (ItemStack, Slot?) -> Boolean) {
		appendSelector { stack, slot -> predicate(stack, slot) }
	}

	fun inverted(block: () -> Unit) {
		invertNewSelectors = true
		block()
		invertNewSelectors = false
	}

	fun sortedWith(newComparator: Comparator<StackAndSlot<*>>) {
		comparator = comparator?.thenComparing(newComparator) ?: newComparator
	}

	fun sortedWith(comparatorSupplier: () -> Comparator<StackAndSlot<*>>) {
		sortedWith(comparatorSupplier())
	}

	fun sortedByBestContentMatch(expectedContents: List<ItemStack>) {
		val expectedFrequencies = expectedContents
			.filter { !it.isEmpty }
			.groupingBy { it.item }
			.eachCount()
		sortedWith(
			compareByDescending { stackAndSlot ->
				val currentFrequencies = stackAndSlot.stack.shulkerBoxStacks
					.filter { !it.isEmpty }
					.groupingBy { it.item }
					.eachCount()
				expectedFrequencies.asSequence().fold(0) { acc, (item, count) ->
					 acc + minOf(count, currentFrequencies[item] ?: 0)
				}
			}
		)
	}

	@PublishedApi
	internal fun appendSelector(selector: (ItemStack, Slot?) -> Boolean) {
		val invert = invertNewSelectors
		val currentSelector = this.selector
		this.selector = { stack, slot ->
			currentSelector(stack, slot) && selector(stack, slot) xor invert
		}
	}

	private fun build() =
		StackSelection(
			count,
			item,
			itemStack,
			inShulkerBoxesOnly,
			comparator ?: compareBy { it.stack.count },
			selector
		)

	companion object {
		private val efficientToolCache = Collections.synchronizedMap<BlockState, Boolean>(mutableMapOf())

		fun selectStack(
			count: Int = 0,
			builder: StackSelectionBuilder.() -> Unit
		) = StackSelectionBuilder(count).apply(builder).build()
	}
}

data class StackAndSlot<T : Slot?>(val stack: ItemStack, val slot: T)