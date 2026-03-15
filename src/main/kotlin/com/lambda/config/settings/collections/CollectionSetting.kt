/*
 * Copyright 2025 Lambda
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

package com.lambda.config.settings.collections

import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.lambda.Lambda.gson
import com.lambda.config.Setting
import com.lambda.config.SettingCore
import com.lambda.config.SettingEditorDsl
import com.lambda.config.SettingGroupEditor
import com.lambda.context.SafeContext
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.threading.runSafe
import imgui.ImGui
import imgui.ImGuiListClipper
import imgui.callback.ImListClipperCallback
import imgui.flag.ImGuiChildFlags
import imgui.flag.ImGuiSelectableFlags.DontClosePopups
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import java.lang.reflect.Type

/**
 * This generic collection settings handles all [Comparable] values (i.e., not classes) and serialize
 * their values by calling [Any.toString] and loads them by comparing what's in the [immutableCollection].
 * This behavior is by design. If you wish to store collections of non-comparable values you must use [ClassCollectionSetting].
 *
 * If you wish to use a different codec or simply display values differently, you must create your own
 * collection setting.
 *
 * @see [com.lambda.config.Configurable]
 */
open class CollectionSetting<R : Any>(
	defaultValue: MutableCollection<R>,
	private var immutableCollection: Collection<R>,
	type: Type,
	private val serialize: Boolean,
	val selectionModel: Boolean = false
) : SettingCore<MutableCollection<R>>(
	defaultValue,
	type
) {
	private var searchFilter = ""
	private val strListType =
		TypeToken.getParameterized(Collection::class.java, String::class.java).type

	val selectListeners = mutableListOf<SafeContext.(R) -> Unit>()
	val deselectListeners = mutableListOf<SafeContext.(R) -> Unit>()

	val modelSelected = mutableSetOf<R>()
	val modelSelectedAdd = mutableSetOf<R>()
	val modelSelectedRemove = mutableSetOf<R>()

	context(setting: Setting<*, MutableCollection<R>>)
	override fun ImGuiBuilder.buildLayout() {
		val showReset = setting.isModified
		val resetButtonText = "R"

		if (selectionModel) {
			buildPopupButtonAndModel("${setting.name}##${setting.name}-CollectionSettingPopup")
		} else {
			buildComboBox("item") { it.toString() }
		}

		if (showReset) {
			sameLine()
			button(resetButtonText) {
				setting.reset()
			}
		}
	}

	/**
	 * Builds a popup for editing the selection. It displays two lists side by side: available elements on the left and selected elements on the right.
	 * Available elements represents all elements from the [immutableCollection] that are not currently in the setting's value, while
	 * selected elements represents all elements selected in the [value] list.
	 * Changes are only applied after the user clicks the "Save" button. When existing using the popup without saving changes are discarded.
	 * Users can move items between the lists using ">>" and "<<" buttons. A search box allows filtering items in both lists. The popup also includes
	 * "Select All" and "Clear Search" buttons for convenience.
	 */
	context(setting: Setting<*, MutableCollection<R>>)
	private fun ImGuiBuilder.buildPopupButtonAndModel(popupName: String) {
		val childWidth = 350f
		val childHeight = 500f
		val buttonColumnWidth = 60f
		val totalWidth = (childWidth + 10f) * 2 + buttonColumnWidth + 20f
		val totalHeight = childHeight + 190f

		if (ImGui.button("${setting.name}: Edit Selection <${value.size} of ${immutableCollection.size} selected>##${setting.name}-Edit")) {
			ImGui.openPopup(popupName)
		}

		ImGui.setNextWindowSize(totalWidth, totalHeight)
		val pOpen = ImBoolean(true)
		if (ImGui.beginPopupModal(popupName, pOpen, ImGuiWindowFlags.NoResize)) {
			val availableItems = (immutableCollection.filter { it !in value && it !in modelSelectedAdd } + modelSelectedRemove).toMutableSet()
			val selectedItems = (value.toList() + modelSelectedAdd - modelSelectedRemove).toMutableSet()

			inputText("##${setting.name}-SearchBox", ::searchFilter)
			sameLine()
			button("X##${setting.name}-ClearSearch") {
				searchFilter = ""
			}
			sameLine()
			button("Select All##${setting.name}-SelectAll") {
				immutableCollection.filter { item ->
					val q = searchFilter.trim()
					if (q.isEmpty()) true
					else item.toString().contains(q, ignoreCase = true)
				}.forEach { item ->
					modelSelected.add(item)
				}
			}

			ImGui.columns(3, "##${setting.name}-Columns", false)
			ImGui.setColumnWidth(0, childWidth + 10f)
			ImGui.setColumnWidth(1, buttonColumnWidth)

			// Left side: Available elements
			ImGui.text("Values")
			child("##${setting.name}-AvailableChild", childWidth, childHeight, ImGuiChildFlags.Border) {
				availableItems.filter { item ->
					val q = searchFilter.trim()
					if (q.isEmpty()) true
					else item.toString().contains(q, ignoreCase = true)
				}.forEach { item ->
					val selected = item in modelSelected
					selectable("$item##available", selected) {
						modelSelected.add(item)
					}
				}
			}

			ImGui.nextColumn()

			// Middle column: Buttons
			ImGui.dummy(0f, 300f)
			val columnWidth = ImGui.getColumnWidth()
			val buttonWidth = 40f
			ImGui.setCursorPosX(ImGui.getCursorPosX() + (columnWidth - buttonWidth) / 2f)
			if (ImGui.button(">>", buttonWidth, 0f)) {
				modelSelected
					.filter { item ->
						val q = searchFilter.trim()
						if (q.isEmpty()) true
						else item.toString().contains(q, ignoreCase = true)
					}
					.forEach { item ->
						modelSelectedAdd.add(item)
						modelSelectedRemove.remove(item)
					}
				modelSelected.clear()
			}
			ImGui.setCursorPosX(ImGui.getCursorPosX() + (columnWidth - buttonWidth) / 2f)
			if (ImGui.button("<<", buttonWidth, 0f)) {
				modelSelected
					.filter { item ->
						val q = searchFilter.trim()
						if (q.isEmpty()) true
						else item.toString().contains(q, ignoreCase = true)
					}
					.forEach { item ->
						modelSelectedRemove.add(item)
						modelSelectedAdd.remove(item)
					}
				modelSelected.clear()
			}

			ImGui.nextColumn()

			// Right side: Selected elements
			ImGui.text("Selected")
			child("##${setting.name}-SelectedChild", childWidth, childHeight, ImGuiChildFlags.Border) {
				selectedItems.filter { item ->
					val q = searchFilter.trim()
					if (q.isEmpty()) true
					else item.toString().contains(q, ignoreCase = true)
				}
					.forEach { item ->
						val selected = item in modelSelected
						if (ImGui.selectable("$item##selected", selected)) {
							modelSelected.add(item)
						}
					}
			}

			ImGui.columns(1)

			ImGui.separator()
			val closeButtonWidth = 100f
			val windowWidth = ImGui.getWindowWidth()
			ImGui.setCursorPosX((windowWidth - closeButtonWidth) / 2f)
			if (ImGui.button("Save", closeButtonWidth, 0f)) {
				modelSelectedAdd.forEach { item ->
					if (item !in value) {
						value.add(item)
						runSafe { selectListeners.forEach { listener -> listener(item) } }
					}
				}
				modelSelectedRemove.forEach { item ->
					if (item in value) {
						value.remove(item)
						runSafe { deselectListeners.forEach { listener -> listener(item) } }
					}
				}
				modelSelected.clear()
				modelSelectedRemove.clear()
				modelSelectedAdd.clear()
				ImGui.closeCurrentPopup()
			}

			ImGui.endPopup()
		} else {
			modelSelected.clear()
			modelSelectedRemove.clear()
			modelSelectedAdd.clear()
		}
	}

	context(setting: Setting<*, MutableCollection<R>>)
	fun ImGuiBuilder.buildComboBox(itemName: String, toString: (R) -> String) {
		val text = if (value.size == 1) itemName else "${itemName}s"

		combo("##${setting.name}", "${setting.name}: ${value.size} $text") {
			inputText("##${setting.name}-SearchBox", ::searchFilter)

			child(
				strId = "##${setting.name}-ComboOptionsChild",
				childFlags = ImGuiChildFlags.AutoResizeY or ImGuiChildFlags.AlwaysAutoResize,
			) {
				val list = immutableCollection
					.filter { item ->
						val q = searchFilter.trim()
						if (q.isEmpty()) true
						else toString(item).contains(q, ignoreCase = true)
					}

				val listClipperCallback = object : ImListClipperCallback() {
					override fun accept(index: Int) {
						val v = list.getOrNull(index) ?: return
						val selected = value.contains(v)

						selectable(
							label = toString(v),
							selected = selected,
							flags = DontClosePopups
						) {
							if (selected) {
								value.remove(v)
								runSafe { deselectListeners.forEach { listener -> listener(v) } }
							} else {
								value.add(v)
								runSafe { selectListeners.forEach { listener -> listener(v) } }
							}
						}
					}
				}
				ImGuiListClipper.forEach(list.size, listClipperCallback)
			}
		}
	}

	context(setting: Setting<*, MutableCollection<R>>)
	override fun toJson(): JsonElement =
		gson.toJsonTree(value, type)

	context(setting: Setting<*, MutableCollection<R>>)
	override fun loadFromJson(serialized: JsonElement) {
		val strList =
			if (serialize) gson.fromJson(serialized, type)
			else gson.fromJson<Collection<String>>(serialized, strListType)
				.mapNotNull { str -> immutableCollection.find { it.toString() == str } }
				.toMutableList()

		value = strList
	}

	companion object {
		fun <T : CollectionSetting<R>, R : Any> Setting<T, MutableCollection<R>>.onSelect(block: SafeContext.(R) -> Unit) = apply {
			core.selectListeners.add(block)
		}

		fun <T : CollectionSetting<R>, R : Any> Setting<T, MutableCollection<R>>.onDeselect(block: SafeContext.(R) -> Unit) = apply {
			core.deselectListeners.add(block)
		}

		@SettingEditorDsl
		@Suppress("unchecked_cast")
		fun <T : Any> SettingGroupEditor.TypedEditBuilder<Collection<T>>.immutableCollection(collection: Collection<T>) {
			(settings as Collection<CollectionSetting<T>>).forEach { it.immutableCollection = collection }
		}
	}
}
