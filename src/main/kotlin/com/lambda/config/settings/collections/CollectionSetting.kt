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

package com.lambda.config.settings.collections

import com.lambda.config.ConfigEditor
import com.lambda.config.Setting
import com.lambda.config.SettingCore
import com.lambda.config.SettingDsl
import com.lambda.config.SettingEditorDsl
import com.lambda.context.SafeContext
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImGui
import com.lambda.imgui.ImGui.getContentRegionAvail
import com.lambda.imgui.ImGuiListClipper
import com.lambda.imgui.callback.ImListClipperCallback
import com.lambda.imgui.flag.ImGuiChildFlags
import com.lambda.imgui.flag.ImGuiPopupFlags
import com.lambda.imgui.flag.ImGuiSelectableFlags.DontClosePopups
import com.lambda.threading.runSafe
import tools.jackson.databind.JavaType

/**
 * This generic collection settings handles all [Comparable] values (i.e., not classes) and serialize
 * their values by calling [Any.toString] and loads them by comparing what's in the [immutableCollection].
 * This behavior is by design. If you wish to store collections of non-comparable values you must use [ClassCollectionSetting].
 *
 * If you wish to use a different codec or simply display values differently, you must create your own
 * collection setting.
 *
 * @see [com.lambda.config.Config]
 */
open class CollectionSetting<R : Any>(
	defaultValue: MutableCollection<R>,
	var immutableCollection: Collection<R>,
	val type: JavaType,
	val serialize: Boolean,
) : SettingCore<MutableCollection<R>>(
	defaultValue
) {
	override var coreValue
		get() = super.coreValue
		set(newVal) {
			super.coreValue = newVal.toMutableList()
		}

    private var searchFilter = ""

    val selectListeners = mutableListOf<SafeContext.(R) -> Unit>()
    val deselectListeners = mutableListOf<SafeContext.(R) -> Unit>()

	context(_: Setting<*, MutableCollection<R>>)
    override fun ImGuiBuilder.buildLayout() = buildDualPane("item") { it.toString() }

	context(setting: Setting<*, MutableCollection<R>>)
	fun ImGuiBuilder.buildDualPane(itemName: String, toString: (R) -> String) {
		val text = if (settingValue.size == 1) itemName else "${itemName}s"
		val popupId = "##${setting.name}-collection-popup"

		button("${setting.name}: ${settingValue.size} $text") {
			ImGui.openPopup(popupId)
		}

		ImGui.setNextWindowSizeConstraints(500f, 0f, Float.MAX_VALUE, io.displaySize.y * 0.5f)
		popupContextItem(popupId, ImGuiPopupFlags.None) {
			inputText("##${setting.name}-SearchBox", ::searchFilter)

			val q = searchFilter.trim()
			val filteredDeselected = immutableCollection
				.filter { item -> !settingValue.contains(item) && (q.isEmpty() || toString(item).contains(q, ignoreCase = true)) }
			val filteredSelected = immutableCollection
				.filter { item -> settingValue.contains(item) && (q.isEmpty() || toString(item).contains(q, ignoreCase = true)) }

			val availableWidth = getContentRegionAvail().x
			val swapButtonWidth = 30f
			val paneWidth = (availableWidth - swapButtonWidth - style.itemSpacing.x * 2) / 2f
			val paneHeight = 200f

			group {
				textDisabled("Deselected (${filteredDeselected.size})")
				child(
					strId = "##${setting.name}-Deselected",
					width = paneWidth,
					height = paneHeight,
					childFlags = ImGuiChildFlags.Border or ImGuiChildFlags.ResizeX or ImGuiChildFlags.ResizeY,
				) {
					val deselectedCallback = object : ImListClipperCallback() {
						override fun accept(index: Int) {
							val v = filteredDeselected.getOrNull(index) ?: return
							selectable(
								label = toString(v),
								flags = DontClosePopups
							) {
								settingValue.add(v)
								runSafe { selectListeners.forEach { listener -> listener(v) } }
							}
						}
					}
					ImGuiListClipper.forEach(filteredDeselected.size, deselectedCallback)
				}
			}

			sameLine()

			group {
				cursorPosY += paneHeight / 2f
				button("<>", swapButtonWidth) {
					val currentlySelected = settingValue.toList()
					val allItems = immutableCollection.toList()
					settingValue.clear()
					allItems.forEach { item ->
						if (!currentlySelected.contains(item)) {
							settingValue.add(item)
						}
					}
					runSafe {
						currentlySelected.forEach { v -> deselectListeners.forEach { listener -> listener(v) } }
						settingValue.forEach { v -> selectListeners.forEach { listener -> listener(v) } }
					}
				}
			}

			sameLine()

			group {
				textDisabled("Selected (${filteredSelected.size})")
				child(
					strId = "##${setting.name}-Selected",
					width = paneWidth,
					height = paneHeight,
					childFlags = ImGuiChildFlags.Border or ImGuiChildFlags.ResizeX or ImGuiChildFlags.ResizeY,
				) {
					val selectedCallback = object : ImListClipperCallback() {
						override fun accept(index: Int) {
							val v = filteredSelected.getOrNull(index) ?: return
							selectable(
								label = toString(v),
								flags = DontClosePopups
							) {
								settingValue.remove(v)
								runSafe { deselectListeners.forEach { listener -> listener(v) } }
							}
						}
					}
					ImGuiListClipper.forEach(filteredSelected.size, selectedCallback)
				}
			}
		}
	}

	@Suppress("unused")
	companion object {
		@SettingDsl
		fun <T : CollectionSetting<R>, R : Any> Setting<T, MutableCollection<R>>.onSelect(block: SafeContext.(R) -> Unit) = apply {
			core.selectListeners.add(block)
		}

		@SettingDsl
		fun <T : CollectionSetting<R>, R : Any> Setting<T, MutableCollection<R>>.onDeselect(block: SafeContext.(R) -> Unit) = apply {
			core.deselectListeners.add(block)
		}

		@Suppress("unchecked_cast")
        @SettingEditorDsl
        fun <T : Any> ConfigEditor.TypedEditBuilder<Collection<T>>.immutableCollection(collection: Collection<T>) {
            (settings as Collection<CollectionSetting<T>>).forEach { it.immutableCollection = collection }
        }
    }
}
