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

@file:Suppress("unused")

package com.lambda.config.settings.collections

import androidx.compose.runtime.Composable
import com.lambda.config.Config
import com.lambda.config.ConfigEditorD5l
import com.lambda.config.EntryCore
import com.lambda.config.SettingEditBuilder
import com.lambda.config.entries.ConfigEntryDsl
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.context.SafeContext
import com.lambda.gui.settings.CollectionButton
import com.lambda.threading.runSafe
import net.minecraft.item.ItemStack
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

	val selectListeners = mutableListOf<SafeContext.(R) -> Unit>()
	val unsafeSelectListeners = mutableListOf<(R) -> Unit>()
	val deselectListeners = mutableListOf<SafeContext.(R) -> Unit>()
	val unsafeDeselectListeners = mutableListOf<(R) -> Unit>()

	override fun isModified(core: EntryCore<MutableCollection<R>>) =
		with(core) {
			value.size != defaultValue.size || defaultValue.any { !value.contains(it) }
		}

	@Composable
	override fun Gui() = CollectionButton(this)

	open fun label(item: R): String = item.toString()

	open fun icon(item: R): ItemStack? = null

	fun select(item: R) {
		if (item in value) return
		value.add(item)
		unsafeSelectListeners.forEach { it(item) }
		runSafe { selectListeners.forEach { it(item) } }
	}

	fun deselect(item: R) {
		if (!value.remove(item)) return
		unsafeDeselectListeners.forEach { it(item) }
		runSafe { deselectListeners.forEach { it(item) } }
	}

	fun invertSelection() {
		val selected = value.toSet()
		immutableCollection.forEach { if (it in selected) deselect(it) else select(it) }
	}
}

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
fun <T : Any> SettingEditBuilder<Collection<T>>.immutableCollection(collection: Collection<T>) {
	(entries as Collection<CollectionSetting<T>>).forEach {
		it.value.retainAll(collection.toSet())
		it.immutableCollection = collection
	}
}