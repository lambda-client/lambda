
package com.minato.config.settings.collections

import com.minato.config.Config
import com.minato.config.ConfigEditor
import com.minato.config.ConfigEditorD5l
import com.minato.config.entries.ConfigEntryDsl
import com.minato.config.entries.Setting
import com.minato.config.entries.SettingEntryLayer
import com.minato.context.SafeContext
import com.minato.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImGui
import com.lambda.imgui.ImGui.getContentRegionAvail
import com.lambda.imgui.ImGuiListClipper
import com.lambda.imgui.callback.ImListClipperCallback
import com.lambda.imgui.flag.ImGuiChildFlags
import com.lambda.imgui.flag.ImGuiPopupFlags
import com.lambda.imgui.flag.ImGuiSelectableFlags.DontClosePopups
import com.minato.threading.runSafe
import tools.jackson.databind.JavaType

/**
 * This generic collection settings handles all [Comparable] values (i.e., not classes) and serialize
 * their values by calling [Any.toString] and loads them by comparing what's in the [immutableCollection].
 * This behavior is by design. If you wish to store collections of non-comparable values you must use [ClassCollectionSetting].
 *
 * If you wish to use a different codec or simply display values differently, you must create your own
 * collection setting.
 *
 * @see [com.minato.config.Config]
 */
open class CollectionSetting<R : Any>(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<CollectionSetting<R>, MutableCollection<R>>,
	visibility: () -> Boolean,
	defaultValue: MutableCollection<R>,
	var immutableCollection: Collection<R>,
	val type: JavaType,
	val serialize: Boolean,
) : Setting<MutableCollection<R>>(name, description, defaultValue, defaultValue.toMutableList(), layer, config, visibility) {
	override var value: MutableCollection<R>
		get() = super.value
		set(newVal) {
			super.value = newVal.toMutableList()
		}

	private var searchFilter = ""

	val selectListeners = mutableListOf<SafeContext.(R) -> Unit>()
	val unsafeSelectListeners = mutableListOf<(R) -> Unit>()
	val deselectListeners = mutableListOf<SafeContext.(R) -> Unit>()
	val unsafeDeselectListeners = mutableListOf<(R) -> Unit>()

	override val isModified: Boolean
		get() = with(originalCore) {
			value.size != defaultValue.size || defaultValue.any { !value.contains(it) }
		}

	override fun ImGuiBuilder.buildLayout() = buildDualPane("item") { it.toString() }

	fun ImGuiBuilder.buildDualPane(itemName: String, toString: (R) -> String) {
		val text = if (value.size == 1) itemName else "${itemName}s"
		val popupId = "##$name-collection-popup"

		button("$name: ${value.size} $text") {
			ImGui.openPopup(popupId)
		}

		ImGui.setNextWindowSizeConstraints(500f, 0f, Float.MAX_VALUE, io.displaySize.y * 0.5f)
		popupContextItem(popupId, ImGuiPopupFlags.None) {
			inputText("##$name-SearchBox", ::searchFilter)

			val q = searchFilter.trim()
			val filteredDeselected = immutableCollection
				.filter { item -> !value.contains(item) && (q.isEmpty() || toString(item).contains(q, ignoreCase = true)) }
			val filteredSelected = immutableCollection
				.filter { item -> value.contains(item) && (q.isEmpty() || toString(item).contains(q, ignoreCase = true)) }

			val availableWidth = getContentRegionAvail().x
			val swapButtonWidth = 30f
			val paneWidth = (availableWidth - swapButtonWidth - style.itemSpacing.x * 2) / 2f
			val paneHeight = 200f

			group {
				textDisabled("Deselected (${filteredDeselected.size})")
				child(
					strId = "##$name-Deselected",
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
								value.add(v)
								unsafeSelectListeners.forEach { listener -> listener(v) }
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
					val currentlySelected = value.toList()
					val allItems = immutableCollection.toList()
					value.clear()
					allItems.forEach { item ->
						if (!currentlySelected.contains(item)) {
							value.add(item)
						}
					}
					currentlySelected.forEach { v -> unsafeDeselectListeners.forEach { listener -> listener(v) } }
					value.forEach { v -> unsafeSelectListeners.forEach { listener -> listener(v) } }
					runSafe {
						currentlySelected.forEach { v -> deselectListeners.forEach { listener -> listener(v) } }
						value.forEach { v -> selectListeners.forEach { listener -> listener(v) } }
					}
				}
			}

			sameLine()

			group {
				textDisabled("Selected (${filteredSelected.size})")
				child(
					strId = "##$name-Selected",
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
								value.remove(v)
								unsafeDeselectListeners.forEach { listener -> listener(v) }
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
		@ConfigEntryDsl
		fun <T : CollectionSetting<R>, R : Any> T.onSelect(block: SafeContext.(R) -> Unit) =
			apply { selectListeners.add(block) }

		@ConfigEntryDsl
		fun <T : CollectionSetting<R>, R : Any> T.onSelectUnsafe(block: (R) -> Unit) =
			apply { unsafeSelectListeners.add(block) }

		@ConfigEntryDsl
		fun <T : CollectionSetting<R>, R : Any> T.onDeselect(block: SafeContext.(R) -> Unit) =
			apply { deselectListeners.add(block) }

		@ConfigEntryDsl
		fun <T : CollectionSetting<R>, R : Any> T.onDeselectUnsafe(block: (R) -> Unit) =
			apply { unsafeDeselectListeners.add(block) }

		@Suppress("unchecked_cast")
		@ConfigEditorD5l
		fun <T : Any> ConfigEditor.SettingEditBuilder<Collection<T>>.immutableCollection(collection: Collection<T>) {
			(entries as Collection<CollectionSetting<T>>).forEach {
				it.value.retainAll(collection.toSet())
				it.immutableCollection = collection
			}
		}
	}
}