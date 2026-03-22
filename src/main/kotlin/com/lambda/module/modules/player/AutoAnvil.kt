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

package com.lambda.module.modules.player

import com.lambda.context.SafeContext
import com.lambda.event.events.GuiEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.gui.LambdaScreen
import com.lambda.gui.dsl.ImGuiBuilder.child
import com.lambda.gui.dsl.ImGuiBuilder.combo
import com.lambda.gui.dsl.ImGuiBuilder.inputText
import com.lambda.gui.dsl.ImGuiBuilder.window
import com.lambda.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.containers.InventoryContainer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.NamedEnum
import com.lambda.util.Timer
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.item.ItemUtils
import imgui.ImGuiListClipper
import imgui.callback.ImListClipperCallback
import imgui.flag.ImGuiChildFlags
import imgui.flag.ImGuiSelectableFlags.DontClosePopups
import imgui.type.ImBoolean
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.ingame.AnvilScreen
import net.minecraft.component.DataComponentTypes
import net.minecraft.enchantment.Enchantment
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.RenameItemC2SPacket
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKeys
import net.minecraft.screen.AnvilScreenHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import kotlin.time.Duration.Companion.milliseconds


object AutoAnvil : Module(
	name = "AutoAnvil",
	description = "Automatically renames or combines items",
	tag = ModuleTag.PLAYER
) {
	var rename by setting("Rename", false)
	var combine by setting("Combine", false)

	var renameName by setting("Rename Name", "Renamed Item").group(Group.Renaming)
	val itemsToRename by setting("Items to Rename", ItemUtils.shulkerBoxes.toSet(), mutableSetOf()).group(Group.Renaming)
	val combineBooksWithOnlyOneEnchantment by setting("Only one on books", true, description = "Only combine books that have one enchantment on them").group(Group.Combining)

	val delayTimer = Timer()

	/** This queue is used to combat item desynchronization you get on 2b by not trying to move the same items from the same slots multiple times. */
	val lastMovedItems = LimitedDecayQueue<Int>(10, 1000)

	val combineMap = mutableMapOf<String, String>()
	val enchantCombineMap = mutableMapOf<String, String>()
	val searchBox1 = SearchBox("Item 1") { Registries.ITEM.toList().map { it.name.string } }
	val searchBox1Enchants = SearchBox("Item 1 Enchants") { getDaShit().map { it.description.string } }
	val searchBox2 = SearchBox("Item 2") { Registries.ITEM.toSet().map { it.name.string } }
	val searchBox2Enchants = SearchBox("Item 2 Enchants") { getDaShit().map { it.description.string } }

	init {
		listen<TickEvent.Pre> {
			val playerLevel = player.experienceLevel
			if (playerLevel <= 0) return@listen

			val sh = player.currentScreenHandler
			if (sh !is AnvilScreenHandler) return@listen
			if (rename && renameName.isNotEmpty()) {
				if (handleRenaming(sh)) return@listen
			}
			if (combine) {
				handleCombining(sh)
			}
		}

		listen<GuiEvent.NewFrame> {
			if (!combine) return@listen
			if (mc.currentScreen !is AnvilScreen && mc.currentScreen !is LambdaScreen) return@listen
			window("Combine Mapping", open = ImBoolean(true)) {
				text("Combine two items")
				separator()
				if (combineMap.isEmpty()) {
					text("No items")
				}
				val toRemove = mutableListOf<String>()
				combineMap.forEach { (item, item1) ->
					val enchant1 = enchantCombineMap["item1$item"]
					val enchant2 = enchantCombineMap["item2$item1"]
					var i1 = if (enchant1 != null) "$item [$enchant1]" else item
					var i2 = if (enchant2 != null) "$item1 [$enchant2]" else item1
					text("$i1 + $i2")
					sameLine()
					button("X") {
						toRemove.add(item)
					}
				}
				combineMap.keys.removeAll(toRemove.toSet())

				separator()

				searchBox1.buildLayout()
				if (searchBox1.selectedItem == Items.ENCHANTED_BOOK.name.string) {
					searchBox1Enchants.buildLayout()
				}
				searchBox2.buildLayout()
				if (searchBox2.selectedItem == Items.ENCHANTED_BOOK.name.string) {
					searchBox2Enchants.buildLayout()
				}
				smallButton("Add Mapping") {
					val item1 = searchBox1.selectedItem
					val item2 = searchBox2.selectedItem

					val enchant1 = searchBox1Enchants.selectedItem
					val enchant2 = searchBox2Enchants.selectedItem

					if (item1 != null && item2 != null) {
						combineMap[item1] = item2
						if (enchant1 != null) {
							enchantCombineMap["item1$item1"] = enchant1
						}
						if (enchant2 != null) {
							enchantCombineMap["item2$item2"] = enchant2
						}
						searchBox1.selectedItem = null
						searchBox2.selectedItem = null
						searchBox1Enchants.selectedItem = null
						searchBox2Enchants.selectedItem = null
					}
				}
			}
			return@listen
		}
	}

	private fun getDaShit(): List<Enchantment> {
		val registry = MinecraftClient.getInstance().world?.registryManager ?: return emptyList()
		return registry.getOrThrow(RegistryKeys.ENCHANTMENT).toList()
	}

	private fun handleCombining(sh: AnvilScreenHandler) {
		if (!delayTimer.timePassed(150.milliseconds)) return
		for ((item1, item2) in combineMap) {
			val input1 = sh.slots[AnvilScreenHandler.INPUT_1_ID]
			val input2 = sh.slots[AnvilScreenHandler.INPUT_2_ID]
			val output = sh.slots[AnvilScreenHandler.OUTPUT_ID]

			if (input1.stack.item.name.string.equals(item1) && input2.stack.item.name.string.equals(item2)) {
				val done = inventoryRequest {
					click(output.id, 0, SlotActionType.QUICK_MOVE)
				}.submit().done
				if (done) delayTimer.reset()
				return
			}

			val item1Enchant = enchantCombineMap["item1$item1"] // I am going to kill myself
			val item2Enchant = enchantCombineMap["item2$item2"]

			val foundItem1 = StackSelection.selectStack().filterSlots(sh.slots)
				.filter { if (item1Enchant != null) hasEnchantmentByName(it.stack, item1Enchant) else true }
				.filter { it.stack.item != Items.ENCHANTED_BOOK || !combineBooksWithOnlyOneEnchantment || hasOnlyOneEnchantment(it.stack) }
				.firstOrNull { it.stack.item.name.string.equals(item1) }
			val foundItem2 = StackSelection.selectStack().filterSlots(sh.slots)
				.filter { if (item2Enchant != null) hasEnchantmentByName(it.stack, item2Enchant) else true }
				.filter { it.stack.item != Items.ENCHANTED_BOOK || !combineBooksWithOnlyOneEnchantment || hasOnlyOneEnchantment(it.stack) }
				.firstOrNull { it.stack.item.name.string.equals(item2) }

			if (foundItem1 != null && foundItem2 != null && input1.stack.isEmpty && input2.stack.isEmpty) {
				val done = inventoryRequest {
					click(foundItem1.id, 0, SlotActionType.QUICK_MOVE)
					click(foundItem2.id, 0, SlotActionType.QUICK_MOVE)
				}.submit().done
				if (done) delayTimer.reset()
				return
			}
		}
	}

	private fun hasEnchantmentByName(itemStack: ItemStack, enchantmentName: String): Boolean {
		return itemStack.components.get(DataComponentTypes.STORED_ENCHANTMENTS)?.enchantments?.any { it.value().description.string == enchantmentName } == true
	}

	private fun hasOnlyOneEnchantment(itemStack: ItemStack): Boolean {
		val enchantments = itemStack.components.get(DataComponentTypes.STORED_ENCHANTMENTS)?.enchantments ?: return false
		return enchantments.size == 1
	}

	private fun SafeContext.handleRenaming(sh: AnvilScreenHandler): Boolean {
		if (!delayTimer.timePassed(50.milliseconds)) return false

		val output = sh.slots[AnvilScreenHandler.OUTPUT_ID]
		val input1 = sh.slots[AnvilScreenHandler.INPUT_1_ID]
		val input2 = sh.slots[AnvilScreenHandler.INPUT_2_ID]
		val freeInvSlot = StackSelection.selectStack().filterSlots(InventoryContainer.slots).any { it.stack.isEmpty }

		if (!output.stack.isEmpty && !freeInvSlot) return false
		if (hasName(output) && output != null) {
			inventoryRequest {
				click(output.id, 0, SlotActionType.QUICK_MOVE)
			}.submit()
			return true
		}

		if (!input1.stack.isEmpty || !input2.stack.isEmpty) return false

		val slot = itemToRename(sh)
		if (slot != null) {
			val done = inventoryRequest {
				click(slot.id, 0, SlotActionType.QUICK_MOVE)
			}.submit().done
			if (done) {
				lastMovedItems.add(slot.id)
				connection.sendPacket(RenameItemC2SPacket(renameName))
				delayTimer.reset()
				return true
			}
		}
		return false
	}

	private fun hasName(slot: Slot) = slot.stack.customName?.equals(renameName) == true

	private fun itemToRename(sh: AnvilScreenHandler): Slot? {
		return StackSelection.selectStack(count = 1) {
			isOneOfItems(itemsToRename)
		}
			.filterSlots(sh.slots)
			.filter { !lastMovedItems.contains(it.id) }
			.firstOrNull { it.stack.customName == null || it.stack.customName?.equals(renameName) == false }
	}

	enum class Group(override val displayName: String) : NamedEnum {
		Renaming("Renaming"),
		Combining("Combining")
	}

	/**
	 * A utility class to select none or one item from a collection, with a search filter and a scrollable list.
	 */
	class SearchBox(val name: String, val immutableCollectionProvider: () -> Collection<String>) {
		var searchFilter = ""
		var selectedItem: String? = null
		var open = ImBoolean(false)

		fun buildLayout() {
			val text = if (selectedItem == null) "$name: None" else "$name: ${selectedItem ?: "Unknown Item"}"

			combo("$name##Combo", text) {
				inputText("##${name}-SearchBox", ::searchFilter)

				child(
					strId = "##${name}-ComboOptionsChild",
					childFlags = ImGuiChildFlags.AutoResizeY or ImGuiChildFlags.AlwaysAutoResize
				) {
					val list = immutableCollectionProvider.invoke()
						.filter { item ->
							val q = searchFilter.trim()
							if (q.isEmpty()) true
							else item.contains(q, ignoreCase = true)
						}

					val listClipperCallback = object : ImListClipperCallback() {
						override fun accept(index: Int) {
							val v = list.getOrNull(index) ?: return
							val selected = v == selectedItem

							selectable(
								label = v,
								selected = selected,
								flags = DontClosePopups
							) {
								if (selected) {
									selectedItem = null
								} else {
									selectedItem = v
								}
							}
						}
					}
					ImGuiListClipper.forEach(list.size, listClipperCallback)
				}
			}
		}
	}
}